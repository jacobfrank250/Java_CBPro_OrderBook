package liveorderbook;

import java.math.BigDecimal;

class Order {
	String id;
	String side;
	BigDecimal price;
	BigDecimal size;

	// Constructor Declaration of Class
	public Order(String id, String side, BigDecimal price, BigDecimal size) {
		this.id = id;
		this.side = side;
		this.price = price;
		this.size = size;
	}
}