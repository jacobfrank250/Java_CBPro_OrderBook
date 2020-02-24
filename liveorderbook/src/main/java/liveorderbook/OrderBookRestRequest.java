package liveorderbook;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.erc.coinbase.pro.Client;
import org.erc.coinbase.pro.exceptions.CoinbaseException;
import org.erc.coinbase.pro.model.Book;
import org.erc.coinbase.pro.rest.ClientConfig;
import org.erc.coinbase.pro.rest.RESTClient;

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





}