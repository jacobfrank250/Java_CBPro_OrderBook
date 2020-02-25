package liveorderbook;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeMap;

class TestResultManager {
    public static String createTestResultDir(String test)
    {
       

        String testResultDir = FileSystems.getDefault().getPath(".").toString() + "/TestResults/" + test;
        boolean madeDir = new File(testResultDir).mkdir();

        if(madeDir)
        {
            return testResultDir;
        }
        else{
            System.out.println("Sorry couldn't create directory");
            return "";
        }


    }

    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i=0; i<children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    System.out.println("couldnt delete child of " + dir.toString() + ": " + children[i]);
                    return false;
                }
            }

        }
        return dir.delete();

    }

    public static void clearTestResultsDir()
    {
        
        String TestResultsDir = FileSystems.getDefault().getPath(".").toString() + "/TestResults";

        deleteDir(new File(TestResultsDir));
        new File(TestResultsDir).mkdirs();


       
    }


    public static void writeTestResultToFile(String dir, String side, BigDecimal sequenceTested, TreeMap<BigDecimal, List<Order>> socketTreeTested, TreeMap<BigDecimal, List<Order>> restTreeTested,String snapshotTime) throws IOException
    {
        
        String fileName = String.valueOf(dir + "/" + side + ".csv");

        FileWriter fstream = new FileWriter(fileName,false);
        BufferedWriter out = new BufferedWriter(fstream);

        out.append("Price");
        out.append(",");
        out.append("Socket Size");
        out.append(",");
        out.append("Rest Size");
        out.append(",");
        out.append("Socket Num Orders");
        out.append(",");
        out.append("Rest Num Orders");
        out.append(",");
        out.append("Sequence");
        out.append(",");
        out.append("Snapshot Time");
        out.append("\n");

        NavigableSet<BigDecimal> prices = side.equals("bid")? restTreeTested.descendingKeySet() : restTreeTested.navigableKeySet();
        for(BigDecimal price : prices)
        {
            List<Order> restOrders = restTreeTested.get(price);
            BigDecimal restSize = BigDecimal.ZERO;
            int restNumOrders = restOrders.size();
            for(Order order:restOrders){
                restSize = restSize.add(order.size);
            }

            List<Order> socketOrders = socketTreeTested.get(price);
            BigDecimal socketSize = BigDecimal.ZERO;
            int socketNumOrders = socketOrders.size();
            for(Order order:socketOrders){
                socketSize = socketSize.add(order.size);
            }

            if(socketNumOrders != restNumOrders)
            {
                System.out.println("(" + side + ") num orders are not equal at price " + price + "\n-->rest: " + restNumOrders + ", socket: " + socketNumOrders);
            }

            if(socketSize.compareTo(restSize) != 0)
            {
                System.out.println("(" + side +") socket and rest sizes are not equal at price " + price + "\n-->rest: " + restSize + ", socket: " + socketSize);
            }

            out.write(price.setScale(2,RoundingMode.HALF_EVEN) + "," + socketSize.setScale(2,RoundingMode.HALF_EVEN) + "," + restSize.setScale(2,RoundingMode.HALF_EVEN) + "," + socketNumOrders + "," + restNumOrders+ "," + sequenceTested + "," + snapshotTime + "\n");

            out.flush();   // Flush the buffer and write all changes to the disk
                
        }

            out.close();    // Close the file
    }
}