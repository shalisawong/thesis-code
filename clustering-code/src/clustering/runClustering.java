package clustering;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.gson.Gson;

import weka.core.Instances;
import distance.*;
import evaluation.*;

/**
 * Driver file for executing clustering code
 * as a part of the pipeline for simulation
 * creation.
 * 
 * @author Shalisa Pattarawuttiwong
 */
public class runClustering {

	/**
	 * Data to be clustered
	 */
	private static Instances data;
	
    /**
     * Reads in instances from a .arff file
     * @param filename   name of the .arff file
     */
    public static void readInInstances(String filename)  throws Exception{
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        data = new Instances(reader);
    }
	
    /**
     * Given a .json file, clusters the data with the specified options.
     * @param args name of .json file to run clustering with
     * @throws Exception
     */
	public static void main(String[] args) throws Exception{
		JSONParser parser = new JSONParser();
		String jsonInput = args[0];

		try {
			// Read in options from the .json file
			Object obj = parser.parse(new FileReader(jsonInput));
			JSONObject jsonObject = (JSONObject) obj;
			int min_k = Integer.parseInt(jsonObject.get("min_k").toString());
			int max_k = Integer.parseInt(jsonObject.get("max_k").toString());
			double beta = Double.parseDouble(jsonObject.get("beta").toString());
		    String cluster_alg = jsonObject.get("cluster_alg").toString();
			String dist_measure = jsonObject.get("dist_measure").toString();
			String agglo_method = jsonObject.get("agglo_method").toString();
			String arffpath = jsonObject.get("arffpath").toString();
			String cluster_outpath = jsonObject.get("cluster_outpath").toString();
			String gt_outpath = jsonObject.get("gt_outpath").toString();
			
	        readInInstances(arffpath);
			JSONParser subparser = new JSONParser();
			
			// grab ground truth from ground truth .json 
			int[] ground_truth = new int[data.numInstances()];
			try {
				Object subobj = subparser.parse(new FileReader(gt_outpath));
				JSONObject subjsonObject = (JSONObject) subobj;
				String gt_list = subjsonObject.get("ground truth").toString();
				
				Gson gson = new Gson();
				ground_truth = gson.fromJson(gt_list, int[].class);
			} catch (Exception e) {
				e.printStackTrace();
			}

			Map<Integer, int[]> clusters = new HashMap<Integer, int[]>();
			System.out.println("Clustering Algorithm: " + cluster_alg);
			System.out.println("Distance Measure: " + dist_measure);
	        System.out.println("NUM INSTANCES: " + data.numInstances());
			int k = min_k;
			while (k <= max_k) {
		        long startTime = System.nanoTime();
		        DistanceFunction distFn;
		        if (dist_measure.equalsIgnoreCase("euclidean")) {
		        	EuclideanDistance eucDist = new EuclideanDistance();
		        	distFn = eucDist;
		        } else if (dist_measure.equalsIgnoreCase("manhattan")) {
		        	ManhattanDistance manDist = new ManhattanDistance();
		        	distFn = manDist;
		        } else if (dist_measure.equalsIgnoreCase("edit")) {
		        	EditDistance editDist = new EditDistance();
		        	distFn = editDist;
		        } else if (dist_measure.equalsIgnoreCase("hmm")) {
		        	DiscreteHMMDistance hmmDist = new DiscreteHMMDistance();
		        	hmmDist.setNumStates(k);
		        	distFn = hmmDist;
			    } else {
		        	throw new IllegalArgumentException("No valid distance function "
		        			+ "chosen in .json config file.");
		        }

		        if (cluster_alg.equalsIgnoreCase("kmeans")) {
		        	KMeans kmeans = new KMeans(data, distFn);
			        kmeans.setNumClusters(k);
			        kmeans.setNumIterations(100);
			        kmeans.cluster();
			        clusters.put(k, kmeans.getClusters());
			        
		        } else if (cluster_alg.equalsIgnoreCase("kmedoids")) {
		        	KMedoids kmedoids = new KMedoids(data, distFn);
			        kmedoids.setNumClusters(k);
			        kmedoids.setNumIterations(100);
			        kmedoids.cluster();
			        clusters.put(k, kmedoids.getClusters());
			        
		        } else if (cluster_alg.equalsIgnoreCase("hierarchical")) {
		        	System.out.println("Agglomeration Method: " + agglo_method);	
		        	AgglomerationMethod aggloMethod;
				if (agglo_method.equalsIgnoreCase("single")) {
					SingleLinkage singleLink = new SingleLinkage();
					aggloMethod = singleLink;
				} else if (agglo_method.equalsIgnoreCase("complete")) {
					CompleteLinkage completeLink = new CompleteLinkage();
					aggloMethod = completeLink;
				} else if (agglo_method.equalsIgnoreCase("average")) {
					AverageLinkage avgLink = new AverageLinkage();
					aggloMethod = avgLink;
				} else if (agglo_method.equalsIgnoreCase("centroid")) {
					CentroidLinkage centroidLink = new CentroidLinkage();
					aggloMethod = centroidLink;
				} else if (agglo_method.equalsIgnoreCase("median")) {
					MedianLinkage medianLink = new MedianLinkage();
					aggloMethod = medianLink;
				} else if (agglo_method.equalsIgnoreCase("ward")) {
					WardLinkage wardLink = new WardLinkage();
					aggloMethod = wardLink;
				} else if (agglo_method.equalsIgnoreCase("weighted average")) {
					WeightedAverageLinkage weightedAvg = new WeightedAverageLinkage();
					aggloMethod = weightedAvg;
				} else {
					throw new IllegalArgumentException("No valid agglomeration method " 
						+ "chosen in .json config file.");
				}
		            	
				HierAgglo hierAgglo = new HierAgglo(data, distFn, aggloMethod);
		            	hierAgglo.cluster();
		            
		            for (int i = k; i <= max_k; i++) {
		            	hierAgglo.setNumClusters(i);
				        clusters.put(i, hierAgglo.getClusters());
		            	System.out.println("\nCluster " + i + ":\n " +
		            			Arrays.toString(hierAgglo.getClusters()));
		            	
		            	// check for k number of clusters
			        	List<Integer> n = new ArrayList<Integer>();
			        	for (int l: clusters.get(k)) {
			        		if (!n.contains(l)) {
			        			n.add(l);
			        		}
			        	}
			        	if (n.size() != k) {
			        		System.out.println("Number of clusters: " + n.size() + " < k");
			        	}
			        	
				        if (beta >= 1.0) {
				        	DistinguishingPairs dp = new DistinguishingPairs();
				        	DistinguishingPairsAdj dpAdj = new DistinguishingPairsAdj();
				        	CollapsedPairs cp = new CollapsedPairs();
				        	
				        	double dp_eval = dp.evaluate(clusters.get(i), ground_truth);
				        	double dpAdj_eval = dpAdj.evaluate(clusters.get(i), ground_truth);
				        	double cp_eval = cp.evaluate(clusters.get(i), ground_truth);
				        	
				        	System.out.println("Distinguishing Pairs (Rand Index): " + dp_eval);
				        	System.out.println("Distinguishing Pairs Adjusted (Adjusted Rand Index): " + dpAdj_eval);
				        	System.out.println("Collapsed Pairs: " + cp_eval);
				        }
		            }			
		            k = max_k + 1;
		            
		        } else {
		        	throw new IllegalArgumentException("No valid clustering algorithm "
		        			+ "chosen in .json config file.");
		        }
		        
		        if (clusters.get(k) != null) {
		        	System.out.println("\nCluster " + k + ":\n " +
		        			Arrays.toString(clusters.get(k)));
		        	List<Integer> n = new ArrayList<Integer>();
		        	for (int l: clusters.get(k)) {
		        		if (!n.contains(l)) {
		        			n.add(l);
		        		}
		        	}
		        	if (n.size() != k) {
		        		System.out.println("Number of clusters: " + n.size() + " < k");
		        	}
			        if (beta >= 1.0) {
			        	// for all pairs of clusterAlg, distFn: calc distances?
			        	DistinguishingPairs dp = new DistinguishingPairs();
			        	DistinguishingPairsAdj dpAdj = new DistinguishingPairsAdj();
			        	CollapsedPairs cp = new CollapsedPairs();
			        	
			        	double dp_eval = dp.evaluate(clusters.get(k), ground_truth);
			        	double dpAdj_eval = dpAdj.evaluate(clusters.get(k), ground_truth);
			        	double cp_eval = cp.evaluate(clusters.get(k), ground_truth);
			        	
			        	System.out.println("Distinguishing Pairs (Rand Index): " + dp_eval);
			        	System.out.println("Distinguishing Pairs Adjusted (Adjusted Rand Index): " + dpAdj_eval);
			        	System.out.println("Collapsed Pairs: " + cp_eval);
			        }
		        } 
		        long endTime = System.nanoTime();
		        System.out.print("Time elapsed (seconds): ");
		        System.out.println(TimeUnit.NANOSECONDS.toSeconds(endTime - startTime));
		        k++;
			}
				
			// write out clusters in json format
			Gson gson_out = new Gson();
			String json_out = gson_out.toJson(clusters);
			
			try {
				FileWriter file = new FileWriter(cluster_outpath);
				file.write(json_out);
				file.flush();
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			 
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
