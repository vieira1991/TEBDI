package main;

// $example on$
import java.io.Serializable;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.ml.evaluation.RegressionEvaluator;
import org.apache.spark.ml.recommendation.ALS;
import org.apache.spark.ml.recommendation.ALSModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

public class ALSExample {

  // $example on$
  public static class Rating implements Serializable {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int userId;
    private int movieId;
    private float rating;
    private long timestamp;

    public Rating() {}

    public Rating(int userId, int movieId, float rating, long timestamp) {
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

    public float getRating() {
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
  }
  // $example off$

  public static void main(String[] args) {
	// Inside class
	  Logger.getLogger("org").setLevel(Level.ERROR);
	  Logger.getLogger("akka").setLevel(Level.ERROR);
	  SparkConf sparkConf = new SparkConf();
		sparkConf.setAppName("Hello Spark");
		sparkConf.setMaster("local[*]");
	  SparkSession spark = SparkSession
	       .builder()
	       .appName("Application Name")
	       .config(sparkConf)
	       .getOrCreate();
	    

	    // $example on$
	    JavaRDD<Rating> ratingsRDD = spark
	      .read().textFile("u.data").javaRDD()
	      .map(Rating::parseRating);
	    Dataset<Row> ratings = spark.createDataFrame(ratingsRDD, Rating.class);
	    Dataset<Row>[] splits = ratings.randomSplit(new double[]{0.8, 0.2});
	    Dataset<Row> training = splits[0];
	    Dataset<Row> test = splits[1];  


	    // Build the recommendation model using ALS on the training data
	    ALS als = new ALS()
	      .setMaxIter(10)
	      .setRegParam(0.1)
	      .setUserCol("userId")
	      .setItemCol("movieId")
	      .setRatingCol("rating")
	      .setRank(8)
	      .setNonnegative(true);
	 
	    ALSModel model = als.fit(training);

	    // Evaluate the model by computing the RMSE on the test data
	    // Note we set cold start strategy to 'drop' to ensure we don't get NaN evaluation metrics
	    model.setColdStartStrategy("drop");
	    
	    Dataset<Row> predictions = model.transform(test);

	    RegressionEvaluator evaluator = new RegressionEvaluator()
	      .setMetricName("rmse")
	      .setLabelCol("rating")
	      .setPredictionCol("prediction");
	    Double rmse = evaluator.evaluate(predictions);
	    System.out.println("Root-mean-square error = " + rmse);
	    /*
	    // Generate top 10 movie recommendations for each user
	    Dataset<Row> userRecs = model.recommendForAllUsers(10);
	   
	    System.out.println(userRecs.collect());
	
	    // Generate top 10 user recommendations for each movie
	    Dataset<Row> movieRecs = model.recommendForAllItems(10);
	   
	    // Generate top 10 movie recommendations for a specified set of users
	    Dataset<Row> users = ratings.select(als.getUserCol()).distinct().limit(3);
	    Dataset<Row> userSubsetRecs = model.recommendForUserSubset(users, 10);
	    // Generate top 10 user recommendations for a specified set of movies
	    Dataset<Row> movies = ratings.select(als.getItemCol()).distinct().limit(3);
	    Dataset<Row> movieSubSetRecs = model.recommendForItemSubset(movies, 10);
	    // $example off$
	    userRecs.show();
	    movieRecs.show();
	    userSubsetRecs.show();
	    movieSubSetRecs.show();
	  */
    spark.stop();
  }
  
}