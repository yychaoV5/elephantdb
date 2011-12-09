package elephantdb.persistence;

import elephantdb.DomainSpec;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

/**
 * "Shard Creators" inherit from this class.
 * On the client side, openPersistenceForRead is called like this:
 *
 *  (.getCoordinator (DomainSpec/readFromFileSystem fs path))
 *
 *  .getCoordinator returns a new instance of the class referenced by that string.
 */
public abstract class PersistenceCoordinator extends KryoWrapper implements Serializable {
    // Add a static Arguments class here with good defaults.
    public abstract LocalPersistence openPersistenceForRead(String root, DomainSpec spec, Map options)
            throws IOException;
    public abstract LocalPersistence openPersistenceForAppend(String root, DomainSpec spec, Map options)
            throws IOException;
    public abstract LocalPersistence createPersistence(String root, DomainSpec spec, Map options)
            throws IOException;

    public KeySorter getKeySorter() {
        return new IdentityKeySorter();
    }
}
