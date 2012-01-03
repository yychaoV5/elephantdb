(ns elephantdb.common.thrift
  (:require [jackknife.core :as u]
            [jackknife.logging :as log]
            [elephantdb.common.database :as db]
            [elephantdb.common.status :as status])
  (:import [org.apache.thrift7.protocol TBinaryProtocol$Factory]
           [org.apache.thrift7.server THsHaServer THsHaServer$Args]
           [org.apache.thrift7.transport TTransport
            TFramedTransport TSocket TNonblockingServerSocket]          
           [elephantdb.common.database Database]
           [elephantdb.generated ElephantDB
            ElephantDBShared$Iface ElephantDB$Processor
            DomainStatus$_Fields Status
            DomainNotFoundException DomainNotLoadedException
            HostsDownException WrongHostException
            DomainStatus LoadingStatus 
            ReadyStatus FailedStatus ShutdownStatus]))

;; ## Status and Errors

(defn loading-status []
  (DomainStatus/loading (LoadingStatus.)))

(defn failed-status [ex]
  (DomainStatus/failed (FailedStatus. (str ex))))

(defn shutdown-status []
  (DomainStatus/shutdown (ShutdownStatus.)))

(defn ready-status [& {:keys [loading?]}]
  (DomainStatus/ready
   (doto (ReadyStatus.)
     (.set_update_status (when loading?
                           (LoadingStatus.))))))

(defn elephant-status [domain-status-map]
  (Status. domain-status-map))

(extend-type DomainStatus
  status/IStatus
  (ready? [status]
    (= (.getSetField status) DomainStatus$_Fields/READY))
 
  (failed? [status]
    (= (.getSetField status) DomainStatus$_Fields/FAILED))

  (shutdown? [status]
    (= (.getSetField status) DomainStatus$_Fields/SHUTDOWN))
  
  (loading? [status]
    (boolean
     (or (= (.getSetField status) DomainStatus$_Fields/LOADING)
         (and (status/ready? status)
              (.get_update_status (.get_ready status))))))
  
  status/IStateful
  (status [state] state)
  (to-ready [state] (ready-status))
  (to-failed [state msg] (failed-status msg))
  (to-shutdown [state] (shutdown-status))
  (to-loading [state] (if (status/ready? state)
                        (ready-status :loading? true)
                        (loading-status))))

(defn to-thrift [state]
  (condp #(%1 %2) state
    status/ready?    (ready-status)
    status/failed?   (failed-status)
    status/shutdown? (shutdown-status)
    status/loading?  (loading-status)
    status/updating? (ready-status :loading? true)))

(defn domain-not-found-ex [domain]
  (DomainNotFoundException. domain))

(defn domain-not-loaded-ex [domain]
  (DomainNotLoadedException. domain))

(defn wrong-host-ex []
  (WrongHostException.))

(defn hosts-down-ex [hosts]
  (HostsDownException. hosts))

(defn assert-domain
  "If the named domain doesn't exist in the supplied database, throws
  a DomainNotFoundException."
  [database domain-name]
  (when-not (db/domain-get database domain-name)
    (domain-not-found-ex domain-name)))

;; ## Connections

(defn thrift-transport
  [host port]
  (TFramedTransport. (TSocket. host port)))

(defn thrift-server
  [service-handler port]
  (let [args (-> (TNonblockingServerSocket. port)
                 (THsHaServer$Args.)
                 (.workerThreads 64)
                 (.executorService 64)
                 (.protocolFactory (TBinaryProtocol$Factory.))
                 (.processor (ElephantDB$Processor. service-handler)))]
    (THsHaServer. args)))

(defn launch-server!
  [service port interval]
  (let [server (thrift-server service port)]
    (u/register-shutdown-hook #(.stop server))
    (log/info "Preparing database...")
    (db/prepare service)
    (log/info "Starting ElephantDB server...")
    (.serve server)))