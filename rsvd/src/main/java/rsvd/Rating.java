package rsvd;

import java.io.Serializable;

public class Rating implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int userId;
	private int movieId;
	private double rating;
	private long timestamp;

	public Rating() {
	}

	public Rating(int userId, int movieId, double rating, long timestamp) {
		this.userId = userId;
		this.movieId = movieId;
		this.rating = rating;
		this.timestamp = timestamp;
	}

	public int getUserId() {
		return userId;
	}

	public int getMovieId() {
		return movieId;
	}

	public double getRating() {
		return rating;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public static Rating parseRating(String str) {
		String[] fields = str.split("\t");
		if (fields.length != 4) {
			throw new IllegalArgumentException("Each line must contain 4 fields");
		}
		int userId = Integer.parseInt(fields[0]);
		int movieId = Integer.parseInt(fields[1]);
		float rating = Float.parseFloat(fields[2]);
		long timestamp = Long.parseLong(fields[3]);
		return new Rating(userId, movieId, rating, timestamp);
	}

	@Override
	public String toString() {
		return "Rating [userId=" + userId + ", movieId=" + movieId + ", rating=" + rating + ", timestamp=" + timestamp
				+ "]";
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public void setMovieId(int movieId) {
		this.movieId = movieId;
	}

	public void setRating(double rating) {
		this.rating = rating;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	
}
