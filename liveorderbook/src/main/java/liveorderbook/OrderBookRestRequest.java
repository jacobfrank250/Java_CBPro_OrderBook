package liveorderbook;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.erc.coinbase.pro.Client;
import org.erc.coinbase.pro.exceptions.CoinbaseException;
import org.erc.coinbase.pro.model.Book;
import org.erc.coinbase.pro.rest.ClientConfig;
import org.erc.coinbase.pro.rest.RESTClient;
// import test.java.liveorderbook.AggregatedBook;

class OrderBookRestRequest {
    List<List<String>> bids;
    List<List<String>> asks;
    BigDecimal sequence;

    public OrderBookRestRequest() throws CoinbaseException {
        ClientConfig config = new ClientConfig();
		config.setBaseUrl("https://api.pro.coinbase.com");
		config.setPublicKey("add28ba503bf0ce93180e0a47b6c36be");
		config.setSecretKey("uiBn7USapxsSLHTkPQokSrk9cvTH8pzEUnBq6QeVmO8m+/+tEAa3F5YsuL43ZLviKcxx3+F2vnMaH+inqKx24g==");
        config.setPassphrase("9moja018n4a");

        Client client = new RESTClient(config);

        Book productList = client.getProductOrderBook("BTC-USD", 3);

        this.bids = new ArrayList<>();
        for (String[] bid : productList.getBids()) {
            List<String> b = Arrays.asList(bid);
            bids.add(b);
        }

        this.asks = new ArrayList<>();
        for (String[] ask : productList.getAsks()) {
            List<String> a = Arrays.asList(ask);
            this.asks.add(a);
        }

        this.sequence = new BigDecimal(productList.getSequence());
    }

    public static String getTime() throws CoinbaseException {
        ClientConfig config = new ClientConfig();
        config.setBaseUrl("https://api.pro.coinbase.com");
        config.setPublicKey("add28ba503bf0ce93180e0a47b6c36be");
        config.setSecretKey("uiBn7USapxsSLHTkPQokSrk9cvTH8pzEUnBq6QeVmO8m+/+tEAa3F5YsuL43ZLviKcxx3+F2vnMaH+inqKx24g==");
        config.setPassphrase("9moja018n4a");

        Client client = new RESTClient(config);

        Date date = client.getTime();
        DateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss:SSS");
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        String formatted = format.format(date);
        format.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        formatted = format.format(date);
        // System.out.println(formatted);
        return formatted;
    }

    public static AggregatedBook L2RestRequest() throws CoinbaseException {
        ClientConfig config = new ClientConfig();
		config.setBaseUrl("https://api.pro.coinbase.com");
		config.setPublicKey("add28ba503bf0ce93180e0a47b6c36be");
		config.setSecretKey("uiBn7USapxsSLHTkPQokSrk9cvTH8pzEUnBq6QeVmO8m+/+tEAa3F5YsuL43ZLviKcxx3+F2vnMaH+inqKx24g==");
        config.setPassphrase("9moja018n4a");

        Client client = new RESTClient(config);

        Book productList = client.getProductOrderBook("BTC-USD", 2);
        return new AggregatedBook(productList.getSequence(), productList.getAsks(), productList.getBids());
        
    }





}