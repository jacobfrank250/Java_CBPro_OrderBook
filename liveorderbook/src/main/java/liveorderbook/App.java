
package liveorderbook;

import java.net.URI;



public class App 
{
  
    public static void main( final String[] args) {
		final OrderBook ob = new OrderBook(URI.create("wss://ws-feed.pro.coinbase.com"));

		ob.connect();

	}
}
