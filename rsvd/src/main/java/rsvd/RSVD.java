package rsvd;

import static org.apache.spark.sql.functions.callUDF;
import static org.apache.spark.sql.functions.collect_list;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoder;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.api.java.UDF2;
import org.apache.spark.sql.api.java.UDF3;
import org.apache.spark.sql.types.DataTypes;

import scala.collection.JavaConverters;
import scala.collection.Seq;

public class RSVD implements Serializable{	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int maxIter;
	private static double regParam;
	private static double lrate;
	private static int rank;
	private Dataset<Row> itemFactors;
	private Dataset<Row> userFactors;

	public RSVD(int maxIter, double regParam, double lrate, int rank) {
		this.maxIter = maxIter;
		RSVD.regParam = regParam;
		RSVD.lrate = lrate;
		RSVD.rank = rank;
	}

	public void fit(Dataset<Row> dataset) {	
		SparkSession spark = dataset.sparkSession();
	
		Encoder<Rating> ratingEncoder = Encoders.bean(Rating.class); 
	    Dataset<Rating> train = dataset.as(ratingEncoder);
	    JavaRDD<Rating> trainSet = train.javaRDD();
	    
		int epochs = 0;

		JavaRDD<UserTuple> user = trainSet.map(rating -> new UserTuple(rating.getUserId(), RSVD.rank)).distinct();
		user = user.map(tuple -> tuple.generateFactors());
		
		JavaRDD<ItemTuple> item = trainSet.map(rating -> new ItemTuple(rating.getMovieId(), RSVD.rank)).distinct();
		item = item.map(tuple -> tuple.generateItemFactors());
		
		Dataset<Row> P = spark.createDataFrame(user, UserTuple.class);		
		Dataset<Row> Q = spark.createDataFrame(item, ItemTuple.class);	
		
		Dataset<Row> uj = dataset.join(P, dataset.col("userId").equalTo(P.col("idUser")));		
		uj = uj.join(Q, uj.col("movieId").equalTo(Q.col("itemId")));		
		uj = uj.select( uj.col("userId"), uj.col("movieId"), uj.col("rating"), uj.col("itemFactors"), uj.col("userFactors"));
		uj = uj.orderBy("userId");
		
		Dataset<Row> U = uj.groupBy(uj.col("userId"), uj.col("userFactors")).agg(collect_list("movieId").as("itemIds"), collect_list("rating").as("ratings"), collect_list("itemFactors").as("itemFactors"));
		Dataset<Row> V = uj.groupBy(uj.col("movieId"), uj.col("itemFactors")).agg(collect_list("userId").as("userIds"), collect_list("rating").as("ratings"), collect_list("userFactors").as("userFactors"));
 
		spark.sqlContext().udf().register("trainU", trainU, DataTypes.createArrayType(DataTypes.DoubleType));
		spark.sqlContext().udf().register("trainV", trainV, DataTypes.createArrayType(DataTypes.DoubleType));

	    Dataset<Row> fatoresU = U.select(U.col("userId"), U.col("ratings"), U.col("itemFactors"),  callUDF("trainU", U.col("userFactors"), U.col("itemFactors"), U.col("ratings")).as("userFactors"));
	    Dataset<Row> fatoresV = V.select(V.col("movieId"), V.col("ratings"), V.col("userFactors"),  callUDF("trainV", V.col("itemFactors"), V.col("userFactors"), V.col("ratings")).as("itemFactors"));
		
	    while(epochs < this.maxIter){
			epochs+=1;	
			//train the model
			fatoresU = fatoresU.select(fatoresU.col("userId"), fatoresU.col("ratings"), fatoresU.col("itemFactors"),  callUDF("trainU", fatoresU.col("userFactors"), fatoresU.col("itemFactors"), fatoresU.col("ratings")).as("userFactors"));
			fatoresV = fatoresV.select(fatoresV.col("movieId"), fatoresV.col("ratings"), fatoresV.col("userFactors"),  callUDF("trainV", fatoresV.col("itemFactors"), fatoresV.col("userFactors"), fatoresV.col("ratings")).as("itemFactors"));
		}
	
		this.userFactors = fatoresU.select(fatoresU.col("userId").as("idUser"), fatoresU.col("userFactors"));
		this.itemFactors = fatoresV.select(fatoresV.col("movieId").as("itemId"), fatoresV.col("itemFactors"));	
	}
	
	public Dataset<Row> transform(Dataset<Row> dataset){
		SparkSession spark = dataset.sparkSession();
		
		Dataset<Row> dju = dataset.join(this.userFactors, dataset.col("userId").equalTo(this.userFactors.col("idUser")),
				"left");

		Dataset<Row> row = dju.join(this.itemFactors, dataset.col("movieId").equalTo(this.itemFactors.col("itemId"))
						, "left").drop("itemId");		
		
		spark.sqlContext().udf().register("predict", predict, DataTypes.DoubleType);
	   
	    Dataset<Row> result = row.select(dataset.col("*"), callUDF("predict", row.col("userFactors"), row.col("itemFactors")).as("prediction"));
	    result = result.where("prediction !=0 ");
	    
	    return result;		
	}	
	
	private static UDF2<Seq<Double>, Seq<Double>, Double> predict = new UDF2<Seq<Double>, Seq<Double>, Double>() {
	    			
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		
		private double dotProduct(List<Double> p, List<Double> q) {
			double dot = 0.0;

			for (int i = 0; i < RSVD.rank; i++) {
				dot += p.get(i) * q.get(i);
			}

			return dot;
		}
		
		@Override
		public Double call(Seq<Double> userFactors, Seq<Double> itemFactors) throws Exception {
						
			if(userFactors == null || itemFactors==null){
				return 0.0;
			}else{
				List<Double> P = scala.collection.JavaConversions.seqAsJavaList(userFactors);
				List<Double> Q = scala.collection.JavaConversions.seqAsJavaList(itemFactors);
				return dotProduct(P, Q);
			}			
		}

		

	};
	

private static UDF3< Seq<Double>, Seq<Seq<Double>>, Seq<Double>, Seq<Double>> trainU = new UDF3< Seq<Double>, Seq<Seq<Double>>, Seq<Double>, Seq<Double>>() {
		
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		
		private List<Double> scalarProduct(double scalar, List<Double> p) {
			List<Double> product = new ArrayList<>(RSVD.rank);

			for (int i = 0; i < p.size(); i++) {
				if(product.size()<RSVD.rank){
					product.add (scalar * p.get(i));
				}else{
					product.set (i,scalar * p.get(i));
				}
			}

			return product;
		}
		
		private double dotProduct(List<Double> p, List<Double> q) {
			double dot = 0.0;

			for (int i = 0; i < RSVD.rank; i++) {
				dot += p.get(i) * q.get(i);
			}

			return dot;
		}
		
		private List<Double> sub(List<Double> temp1, List<Double> temp2) {
			List<Double> sub = new ArrayList<>(RSVD.rank);

			for (int i = 0; i < RSVD.rank; i++) {
				if(sub.size() < RSVD.rank){
					sub.add(temp1.get(i) - temp2.get(i));
				}else{
					sub.set(i,temp1.get(i) - temp2.get(i));
				}				
			}

			return sub;
		}
		
		private List<Double> add(List<Double> p, List<Double> temp4) {
			List<Double> add = new ArrayList<>(RSVD.rank);

			for (int i = 0; i < RSVD.rank; i++) {
				if(add.size()<RSVD.rank){
					add.add( p.get(i) + temp4.get(i));
				}else{
					add.set(i, p.get(i) + temp4.get(i));
				}			
			}

			return add;
		}
		
		@Override
		public Seq<Double> call(Seq<Double> userFactors, Seq<Seq<Double>> movieFactors, Seq<Double> rating) throws Exception {
			List<Double> ratings = scala.collection.JavaConversions.seqAsJavaList(rating);
			List<Double> P = scala.collection.JavaConversions.seqAsJavaList(userFactors);
			List<Seq<Double>> Q = scala.collection.JavaConversions.seqAsJavaList(movieFactors);
			List<Double> q = null;
			List<Double> temp1 = null;
			List<Double> temp2 = null;
			List<Double> temp3 = null;
			List<Double> temp4 = null;
			
			double erro =0;
			
			for(int i=0; i<ratings.size();i++){
				q =  scala.collection.JavaConversions.seqAsJavaList(Q.get(i));
				
				erro = ratings.get(i) - dotProduct(P,q );
				temp1 = scalarProduct(erro, q);
				temp2 = scalarProduct(RSVD.regParam, P);
				temp3 = sub(temp1, temp2);
				temp4 = scalarProduct(RSVD.lrate, temp3);
				P = add(P, temp4);
			}
			
			Seq<Double> seq = JavaConverters.asScalaIteratorConverter(P.iterator()).asScala().toSeq();
			return seq;
		}
	
		 
			
	};
	
	
private static UDF3< Seq<Double>, Seq<Seq<Double>>, Seq<Double>, Seq<Double>> trainV = new UDF3< Seq<Double>, Seq<Seq<Double>>, Seq<Double>, Seq<Double>>() {
		
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		
		private List<Double> scalarProduct(double scalar, List<Double> p) {
			List<Double> product = new ArrayList<>(RSVD.rank);

			for (int i = 0; i < p.size(); i++) {
				if(product.size()<RSVD.rank){
					product.add (scalar * p.get(i));
				}else{
					product.set (i,scalar * p.get(i));
				}
			}

			return product;
		}
		
		private double dotProduct(List<Double> p, List<Double> q) {
			double dot = 0.0;

			for (int i = 0; i < RSVD.rank; i++) {
				dot += p.get(i) * q.get(i);
			}

			return dot;
		}
		
		private List<Double> sub(List<Double> temp1, List<Double> temp2) {
			List<Double> sub = new ArrayList<>(RSVD.rank);

			for (int i = 0; i < RSVD.rank; i++) {
				if(sub.size() < RSVD.rank){
					sub.add(temp1.get(i) - temp2.get(i));
				}else{
					sub.set(i,temp1.get(i) - temp2.get(i));
				}				
			}

			return sub;
		}
		
		private List<Double> add(List<Double> p, List<Double> temp4) {
			List<Double> add = new ArrayList<>(RSVD.rank);

			for (int i = 0; i < RSVD.rank; i++) {
				if(add.size()<RSVD.rank){
					add.add( p.get(i) + temp4.get(i));
				}else{
					add.set(i, p.get(i) + temp4.get(i));
				}			
			}

			return add;
		}
		
		@Override
		public Seq<Double> call(Seq<Double> movieFactors, Seq<Seq<Double>> userFactors, Seq<Double> rating) throws Exception {
			List<Double> ratings = scala.collection.JavaConversions.seqAsJavaList(rating);
			List<Double> Q = scala.collection.JavaConversions.seqAsJavaList(movieFactors);
			List<Seq<Double>> P = scala.collection.JavaConversions.seqAsJavaList(userFactors);
			List<Double> p = null;
			List<Double> temp1 = null;
			List<Double> temp2 = null;
			List<Double> temp3 = null;
			List<Double> temp4 = null;
			
			
			
			double erro =0;
			
			for(int i=0; i<ratings.size();i++){
				p =  scala.collection.JavaConversions.seqAsJavaList(P.get(i));
				
				erro = ratings.get(i) - dotProduct(p,Q );
				temp1 = scalarProduct(erro, p);
				temp2 = scalarProduct(RSVD.regParam, Q);
				temp3 = sub(temp1, temp2);
				temp4 = scalarProduct(RSVD.lrate, temp3);
				Q = add(Q, temp4);
			}
			
			Seq<Double> seq = JavaConverters.asScalaIteratorConverter(Q.iterator()).asScala().toSeq();
			return seq;
		}
	
		 
			
	};
	
	@Override
	public String toString() {
		return this.maxIter + "; "+ RSVD.lrate + "; " + RSVD.rank + "; " + RSVD.regParam;
	}

	
	
}
