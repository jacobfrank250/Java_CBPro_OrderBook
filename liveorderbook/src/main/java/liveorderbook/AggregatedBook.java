package liveorderbook;

public class AggregatedBook {
	/** The sequence. */
	private String sequence;

	/** The bids. */
	private String[][] bids;

	/** The aks. */
	private String[][] asks;

	public AggregatedBook(String sequence, String[][] asks, String[][] bids) {
		this.setSequence(sequence);
		this.setAsks(asks);
		this.setBids(bids);
	}

	public void setAsks(String[][] asks) {
		this.asks = asks;
	}

	
	public String[][] getAsks()
	{
		return asks;
	}

	public void setBids(String[][] bids) {
		this.bids = bids;
	}

	public String[][] getBids()
	{
		return bids;
	}

	public void setSequence(String sequence) {
		this.sequence = sequence;
	}

	
	public String getSequence()
	{
		return sequence;
	}
}