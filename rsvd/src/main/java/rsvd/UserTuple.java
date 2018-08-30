package rsvd;

import java.io.Serializable;
import java.util.Arrays;

public class UserTuple implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private long idUser;
	private int rank;
	private double[] userFactors = null;
	
	public UserTuple(long idUser,int rank) {
		this.rank = rank;
		this.idUser = idUser;	
//		this.userFactors = random();
	}	

	public long getIdUser() {
		return this.idUser;
	}

	private double[] random() {
		double[] temp = new double[this.rank];
		
		for (int i = 0; i < this.rank; i++) {
			temp[i] = Math.random();
		}
		return temp;
	}
	
	public UserTuple generateFactors() {
		this.userFactors =  random();
		return this;
	}
	
	public double[] getUserFactors() {
		return this.userFactors;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (idUser ^ (idUser >>> 32));
		result = prime * result + Arrays.hashCode(userFactors);
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
		UserTuple other = (UserTuple) obj;
		if (idUser != other.idUser)
			return false;
		if (!Arrays.equals(userFactors, other.userFactors))
			return false;
		return true;
	}


	
}
