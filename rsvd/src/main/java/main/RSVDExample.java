package main;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.ml.evaluation.RegressionEvaluator;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import rsvd.RSVD;
import rsvd.Rating;

public class RSVDExample {

	public static void main(String[] args) {			
	
		  Logger.getLogger("org").setLevel(Level.ERROR);
		  Logger.getLogger("akka").setLevel(Level.ERROR);
		  SparkConf sparkConf = new SparkConf();
			sparkConf.setAppName("Hello Spark");
			sparkConf.setMaster("local[4]");
		  SparkSession spark = SparkSession
		       .builder()
		       .appName("Application Name")
		       .config(sparkConf)
		       .getOrCreate();

		    JavaRDD<Rating> ratingsRDD = spark
		      .read().textFile("u.data").javaRDD()
		      .map(Rating::parseRating);
		    Dataset<Row> ratings = spark.createDataFrame(ratingsRDD, Rating.class);
		    Dataset<Row>[] splits = ratings.randomSplit(new double[]{0.8, 0.2});
		    Dataset<Row> training = splits[0];
		    Dataset<Row> test = splits[1];      
 	   
		    RSVD rsvd = new RSVD(30, 0.1, 0.01, 15);  	    
		    rsvd.fit(training);
		    
		    try{
				Dataset<Row> predictions  = rsvd.transform(test); 
				RegressionEvaluator evaluator = new RegressionEvaluator()
				  	      .setMetricName("rmse")
				  	      .setLabelCol("rating")
				  	      .setPredictionCol("prediction");
				Double rmse = evaluator.evaluate(predictions);
				System.out.println("Root-mean-square error = " + rmse);
		   }catch (Exception e) {
			e.printStackTrace();
		}finally {
			 spark.stop();			 
		}	 
  }

}