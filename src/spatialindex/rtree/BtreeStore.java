package spatialindex.rtree;

import jdbm.RecordManagerFactory;
import jdbm.btree.BTree;
import jdbm.helper.ComparableComparator;
import jdbm.helper.DefaultSerializer;
import jdbm.helper.TupleBrowser;
import jdbm.recman.CacheRecordManager;
import neustore.base.FloatData;
import neustore.base.IntKey;
import neustore.base.KeyData;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

/**
 * BTreeStore用来管理关键字在文档中的权重
 */
public class BtreeStore {

    CacheRecordManager cacheRecordManager;
    long recid;
    BTree btree;
    int IO = 0;

    public BtreeStore(String name, boolean isCreate) throws IOException {
        cacheRecordManager = new CacheRecordManager(RecordManagerFactory.createRecordManager(name), 1000, true);

        if (!isCreate) {
            recid = cacheRecordManager.getNamedObject("0");
//			System.out.println("real btree id " + recid);
            btree = BTree.load(cacheRecordManager, recid);
        } else {
            btree = BTree.createInstance(cacheRecordManager, ComparableComparator.INSTANCE, DefaultSerializer.INSTANCE, DefaultSerializer.INSTANCE, 200);
            cacheRecordManager.setNamedObject("0", btree.getRecid());
        }
    }

    public static BtreeStore process(String inputFileName, String btreeName, boolean isCreate) throws Exception {
        BtreeStore bs = new BtreeStore(btreeName, isCreate);
//		inputFileName = System.getProperty("user.dir") + File.separator + "src" +
//				File.separator + "regressiontest" + File.separator + "test3" + File.separator + inputFileName + ".gz";
        if (isCreate) {
//			BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(inputFileName))));
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(inputFileName)));

            String line;
            String[] temp;
            int count = 0;
            while ((line = in.readLine()) != null) {
                temp = line.split(",");
                int id = Integer.parseInt(temp[0]);

                Vector<KeyData> document = new Vector<>();
                for (int i = 6; i < temp.length; i++) {
                    String[] kv = temp[i].split(" ");
                    int wordID = Integer.parseInt(kv[0]);
                    float weight = Float.parseFloat(kv[1]);
                    IntKey key = new IntKey(wordID);
                    FloatData data = new FloatData(weight, weight);
                    KeyData keydata = new KeyData(key, data);
                    document.add(keydata);
                }
                bs.insertDoc(id, document);

                if (count % 1000 == 0) {
                    System.out.println(count);
                    bs.cacheRecordManager.commit();
                }
                count++;
            }
            bs.cacheRecordManager.commit();
            in.close();
        }
//		System.out.println("Step one finished");
        return bs;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: BtreeStore inputfile btreename.");
            System.exit(-1);
        }
        String file = args[0];
        String btreename = args[1];

        BtreeStore bs = new BtreeStore(btreename, true);

        FileInputStream fin = new FileInputStream(file);
        GZIPInputStream gzis = new GZIPInputStream(fin);
        InputStreamReader xover = new InputStreamReader(gzis);
        BufferedReader is = new BufferedReader(xover);
        String line;
        String[] temp;
        double[] f = new double[2];

        int count = 0;

        /**
         *  B树data文件格式
         *  id,?,?,wordID weight,wordID weight,...\n
         *  id,?,?,wordID weight,wordID weight,...\n
         *  ...
         */
        while ((line = is.readLine()) != null) {
            temp = line.split(",");
            int id = Integer.parseInt(temp[0]);

            Vector document = new Vector();
            for (int j = 3; j < temp.length; j++) {
                String[] tt = temp[j].split(" ");
                int wordID = Integer.parseInt(tt[0]);
                float weight = Float.parseFloat(tt[1]);
                IntKey key = new IntKey(wordID);
                FloatData data = new FloatData(weight, weight);
                KeyData keydata = new KeyData(key, data);
                document.add(keydata);
            }
            bs.insertDoc(id, document);

            if (count % 100 == 0) {
                System.out.println(count);
                bs.cacheRecordManager.commit();
            }
            count++;
        }
        bs.cacheRecordManager.commit();

        System.out.println("finished!");
    }


    private void insertDoc(int id, Vector document) throws IOException {
        Object var = btree.insert(id, document, false);
        if (var != null) {
            System.out.println("Btree insertion error: duplicate keys.");
            //System.exit(-1);
        }
    }


    public Vector getDoc(int id) throws IOException {
        Object var = btree.find(id);
//    	if(var == null){
//    		System.out.println("Document not found " + id);
//    		System.exit(-1);
//    	}
        IO++;
        return (Vector) var;
    }

    public int size() {
        return btree.size();
    }

    public int getIO() {
        return IO;
    }

    public TupleBrowser getBrowser() throws IOException {
        return btree.browse();
    }
}
