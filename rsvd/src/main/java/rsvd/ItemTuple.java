package rsvd;

import java.io.Serializable;
import java.util.Arrays;

public class ItemTuple implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private long itemId;
	private double[] itemFactors = null;
	private int rank;
	
	public ItemTuple(long itemId, int rank) {	
		this.itemId = itemId;
		this.rank = rank;	
	}
	
	private double[] random() {
		double[] temp = new double[this.rank];
		
		for (int i = 0; i < this.rank; i++) {
			temp[i] = Math.random();
		}
		return temp;
	}
	
	public long getItemId() {
		return this.itemId;
	}
	
	public ItemTuple generateItemFactors(){
		this.itemFactors = random();
		return this;
	}
	public double[] getItemFactors() {
		return itemFactors;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(itemFactors);
		result = prime * result + (int) (itemId ^ (itemId >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ItemTuple other = (ItemTuple) obj;
		if (!Arrays.equals(itemFactors, other.itemFactors))
			return false;
		if (itemId != other.itemId)
			return false;
		return true;
	}

	
	
}
