package elephantdb.persistence;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import org.apache.log4j.Logger;

public class JavaBerkDB extends LocalPersistenceFactory {
    public static Logger LOG = Logger.getLogger(JavaBerkDB.class);


    @Override
    public LocalPersistence openPersistenceForRead(String root, Map options) throws IOException {
        return new JavaBerkDBPersistence(root, options, true);
    }

    @Override
    public LocalPersistence openPersistenceForAppend(String root, Map options) throws IOException {
        return new JavaBerkDBPersistence(root, options, false);
    }

    @Override
    public LocalPersistence createPersistence(String root, Map options) throws IOException {
        return new JavaBerkDBPersistence(root, options, false);
    }

    public static class JavaBerkDBPersistence implements LocalPersistence {
        Environment _env;
        Database _db;

        private static final String DATABASE_NAME = "elephant";

        public JavaBerkDBPersistence(String root, Map options, boolean readOnly) {
            new File(root).mkdirs();
            EnvironmentConfig envConf = new EnvironmentConfig();
            envConf.setAllowCreate(true);
            envConf.setReadOnly(readOnly);
            envConf.setLocking(false);
            envConf.setTransactional(false);
            envConf.setConfigParam(EnvironmentConfig.CLEANER_MIN_UTILIZATION, "80");
            envConf.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "false");

            _env = new Environment(new File(root), envConf);

            DatabaseConfig dbConf = new DatabaseConfig();
            dbConf.setAllowCreate(true);
            dbConf.setReadOnly(readOnly);
            dbConf.setDeferredWrite(true);

            _db = _env.openDatabase(null, DATABASE_NAME, dbConf);
        }

        public byte[] get(byte[] key) throws IOException {
            DatabaseEntry val = new DatabaseEntry();
            OperationStatus stat = _db.get(null, new DatabaseEntry(key), val, LockMode.READ_UNCOMMITTED);
            if(stat == OperationStatus.SUCCESS) {
                return val.getData();
            } else {
                return null;
            }
        }

        public void add(byte[] key, byte[] value) throws IOException {
            _db.put(null, new DatabaseEntry(key), new DatabaseEntry(value));
        }

        public void close() throws IOException {
            if(!_db.getConfig().getReadOnly()) {
                LOG.info("Syncing environment at " + _env.getHome().getPath());
                _env.sync();
                LOG.info("Done syncing environment at " + _env.getHome().getPath());

                LOG.info("Cleaning environment log at " + _env.getHome().getPath());
                boolean anyCleaned = false;
                while (_env.cleanLog() > 0) {
                    anyCleaned = true;
                }
                LOG.info("Done cleaning environment log at " + _env.getHome().getPath());
                if (anyCleaned) {
                    LOG.info("Checkpointing environment at " + _env.getHome().getPath());
                    CheckpointConfig checkpoint = new CheckpointConfig();
                    checkpoint.setForce(true);
                    _env.checkpoint(checkpoint);
                    LOG.info("Done checkpointing environment at " + _env.getHome().getPath());
                }
            }

            _db.close();
            _env.close();
        }

        public CloseableIterator<KeyValuePair> iterator() {
            return new CloseableIterator<KeyValuePair>() {
                Cursor cursor = null;
                KeyValuePair next = null;

                private void cacheNext() {
                    DatabaseEntry key = new DatabaseEntry();
                    DatabaseEntry val = new DatabaseEntry();
                    OperationStatus stat = cursor.getNext(key, val, LockMode.READ_UNCOMMITTED);
                    if(stat == OperationStatus.SUCCESS) {
                        next = new KeyValuePair(key.getData(), val.getData());
                    } else {
                        next = null;
                        close();
                    }
                }

                private void initCursor() {
                    if(cursor==null) {
                        cursor = _db.openCursor(null, null);
                        cacheNext();
                    }                    
                }
                
                public boolean hasNext() {
                    initCursor();
                    return next!=null;
                }

                public KeyValuePair next() {
                    initCursor();
                    if(next==null) throw new RuntimeException("No key/value pair available");
                    KeyValuePair ret = next;
                    cacheNext();
                    return ret;
                }

                public void remove() {
                    throw new UnsupportedOperationException("Not supported.");
                }

                public void close() {
                    if(cursor!=null) cursor.close();
                }
                
            };
        }
        
    }

}
