package elephantdb.hadoop;

import elephantdb.Utils;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.apache.hadoop.io.Writable;

public class ElephantRecordWritable implements Writable {
    byte[] key;
    byte[] val;


    public ElephantRecordWritable() {
        
    }

    public ElephantRecordWritable(byte[] key, byte[] val) {
        this.key = key;
        this.val = val;
    }

    public void write(DataOutput d) throws IOException {
        Utils.writeByteArray(d, key);
        Utils.writeByteArray(d, val);        
    }

    public void readFields(DataInput di) throws IOException {
        this.key = Utils.readByteArray(di);
        this.val = Utils.readByteArray(di);
    }
    
}