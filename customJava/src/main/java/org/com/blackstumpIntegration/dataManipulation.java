package org.com.blackstumpIntegration;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;



public class dataManipulation extends AbstractMediator { 
	
	//private static Logger log = Logger.getLogger(dataManipulation.class.getName());
	private static Log log= LogFactory.getLog(dataManipulation.class.getName());
	
	private static String MySqlURL="jdbc:mysql://localhost:3306/mysqltest?allowPublicKeyRetrieval=true&useSSL=false";
	private static String MySqlUser="root";
	private static String MySqlPass="root";
	
	private static String MySqlInsertQuery ="INSERT INTO blackstump_vrm_daily (timestamp, site_id, instance_id , op1, op2, op3, ip1, ip2, ip3, opT, ipT,Load_Percentage_Solarator,Load_Percentage_Comparision,Consumption_Solarator,Consumption_Comparision,SolaratorCost,ComparisionCost,SolaratorCO2,ComparisionCO2,battery_status,battery_power,solar_yeild,consumption,generator) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	public boolean mediate(MessageContext context) { 
		// TODO Implement your mediation logic here 
		log.info("Inside the dataManipulation - /blackstump/v2/installations/widgets/Graph java class mediator");
		try {
			if (context.getProperty("data_records").toString().isEmpty() && !context.getProperty("stats_responce").toString().startsWith("{") && !context.getProperty("stats_responce").toString().endsWith("}")) {
				log.info("Empty or wrong formatted responce from the vrm API for siteId: " + context.getProperty("siteID").toString());
                return true;
            }
			JSONObject Json_data_records = new JSONObject(context.getProperty("data_records").toString());
			JSONObject stats_responce = new JSONObject();
			if (!context.getProperty("stats_responce").toString().isEmpty() && context.getProperty("stats_responce").toString().startsWith("{") && context.getProperty("stats_responce").toString().endsWith("}")) {
				stats_responce = new JSONObject(context.getProperty("stats_responce").toString());
				log.info("Received data record from vrm stats api for: "+context.getProperty("stats_responce").toString());
            } 
			String[] colheads = {"OP1", "OP2", "OP3","IP1", "IP2", "IP3"};//One to one mapping between colheads and attribute_ids according to the python script
			String[] attribute_ids = {"29", "30", "31", "17", "18", "19"};
			
			List<DataPoint> originalDataIP1 = new ArrayList<>(); //data sets for each and every attribute_ids
			List<DataPoint> originalDataIP2 = new ArrayList<>();
			List<DataPoint> originalDataIP3 = new ArrayList<>();
			List<DataPoint> originalDataOP1 = new ArrayList<>();
			List<DataPoint> originalDataOP2 = new ArrayList<>();
			List<DataPoint> originalDataOP3 = new ArrayList<>();
			List<LocalDateTime> timestampList = new ArrayList<>();
			
			log.info("Received data record from vrm api for: "+context.getProperty("siteID").toString()+"   "+Json_data_records);
			log.info("Received data record from vrm stats api for: "+context.getProperty("siteID").toString()+"   "+stats_responce);
			//========================================================================
			//validate the data from s3_bs_installation_data table
			String pattern = "-?\\d+(\\.\\d+)?";

	        // Create a Pattern object
	        Pattern regexPattern = Pattern.compile(pattern);
	        List<String> inputsFromDB = Arrays.asList("fuel_cost","gen_fuel_cost","sol_gen_size","comp_gen_size","sol_cons_load_25","sol_cons_load_50","sol_cons_load_75","sol_cons_load_100","sol_cons_load_110","com_cons_load_25","com_cons_load_50","com_cons_load_75","com_cons_load_100","com_cons_load_110");
	         for (String input : inputsFromDB) {
	            // Create a Matcher object for each input string
	            Matcher matcher = regexPattern.matcher(context.getProperty(input).toString());

	            // Perform the regex check
	            if (matcher.matches()) {
	            	inputsFromDB.set(inputsFromDB.indexOf(input), context.getProperty(input).toString());
	            } else {
	            	inputsFromDB.set(inputsFromDB.indexOf(input), "1");
	            	log.info(input+" for "+context.getProperty("siteID").toString()+" is not a number in database. hence set 1 as default value");
	            }
	        }
	        
			Double fuel_cost = Double.parseDouble(inputsFromDB.get(0));
			Double gen_fuel_cost = Double.parseDouble(inputsFromDB.get(1));
			Double sol_gen_size = Double.parseDouble(inputsFromDB.get(2));
			Double comp_gen_size = Double.parseDouble(inputsFromDB.get(3));
			Double sol_cons_load_25 = Double.parseDouble(inputsFromDB.get(4));
			Double sol_cons_load_50 = Double.parseDouble(inputsFromDB.get(5));
			Double sol_cons_load_75 = Double.parseDouble(inputsFromDB.get(6));
			Double sol_cons_load_100 = Double.parseDouble(inputsFromDB.get(7));
			Double sol_cons_load_110 = Double.parseDouble(inputsFromDB.get(8));
			Double com_cons_load_25 = Double.parseDouble(inputsFromDB.get(9));
			Double com_cons_load_50 = Double.parseDouble(inputsFromDB.get(10));
			Double com_cons_load_75 = Double.parseDouble(inputsFromDB.get(11));
			Double com_cons_load_100 = Double.parseDouble(inputsFromDB.get(12));
			Double com_cons_load_110 = Double.parseDouble(inputsFromDB.get(13));
			//======================================================================
	        
			//Processing vrm hourly data
	        for (String key : Json_data_records.keySet()) { // iterate through the attribute_ids
	            String value = Json_data_records.get(key).toString();
	            JSONArray jsonArray = new JSONArray(value);//value of attribute ids

	            //handling data sets for single attribute codes
	            for (int i = 0; i < jsonArray.length(); i++) { //iteration of values of attribute codes such as op1,op2 etc
	                JSONArray tempJsonArray = new JSONArray(jsonArray.get(i).toString());
	                
	                if (!JSONObject.NULL.equals(tempJsonArray.get(1)) && !JSONObject.NULL.equals(tempJsonArray.get(0))) {
	                	String stringTimestamp = tempJsonArray.get(0).toString();//get the timestamp of the particular attribute id value
		                Double valueOfTheTimesatmp = Double.parseDouble(tempJsonArray.get(1).toString()); //get the value of the particular
		                                                                                                    // attribute id value
		                long longTimestamp = Long.parseLong(stringTimestamp);//convert timestamp to long

		                LocalDateTime dateTime = LocalDateTime.ofInstant(
		                        Instant.ofEpochSecond(longTimestamp),
		                        ZoneOffset.UTC
		                );// LocalDateTime of the timesatmp
		                
		                if (!timestampList.contains(dateTime.withMinute(0).withSecond(0))) {
		                    timestampList.add(dateTime.withMinute(0).withSecond(0));
		                }

		                if (key.contains(attribute_ids[0])) { //OP1 case
		                    originalDataOP1.add(new DataPoint(dateTime, valueOfTheTimesatmp));
		                } else if (key.contains(attribute_ids[1])){ //OP2 case
		                    originalDataOP2.add(new DataPoint(dateTime, valueOfTheTimesatmp));
		                }else if (key.contains(attribute_ids[2])){ //OP3 case
		                    originalDataOP3.add(new DataPoint(dateTime, valueOfTheTimesatmp));
		                }else if (key.contains(attribute_ids[3])){ //IP1 case
		                    originalDataIP1.add(new DataPoint(dateTime, valueOfTheTimesatmp));
		                }else if (key.contains(attribute_ids[4])){ //IP2 case
		                    originalDataIP2.add(new DataPoint(dateTime, valueOfTheTimesatmp));
		                }else if (key.contains(attribute_ids[5])){ //IP3 case
		                    originalDataIP3.add(new DataPoint(dateTime, valueOfTheTimesatmp));
		                } else {
		                    log.info("Not belongs to a defined attribute ID:" + context.getProperty("siteID").toString()+"/"+ String.valueOf(dateTime)+"-"+valueOfTheTimesatmp);
		                }
	                }
	                
	            }
	        }
	        if (timestampList.isEmpty()) {// retrurn if there is empty results
	        	log.info("No hourly data found.  Hence skip the rest of the flow: " + context.getProperty("siteID").toString());
	        	return true;
	        }
	        Collections.sort(timestampList);
	        LocalDateTime lastHour = timestampList.get(timestampList.size() - 1);
	        //log.info(String.valueOf(lastHour.toEpochSecond(ZoneOffset.UTC)) + "=========================================");
	        
	        
	        timestampList.remove(timestampList.size() - 1);
	        if (timestampList.isEmpty()) {//check again whether the timestampList empty after removing the last item
	        	log.info("No complete hourly data to resample completly. Hence skip the rest of the flow: "+context.getProperty("siteID").toString());
	        	return true;
	        }
	        //call resampling the vrm hourly data
	        Map<LocalDateTime, Double> resampledDataOP1 = resampleHourly(originalDataOP1);
	        Map<LocalDateTime, Double> resampledDataOP2 = resampleHourly(originalDataOP2);
	        Map<LocalDateTime, Double> resampledDataOP3 = resampleHourly(originalDataOP3);
	        Map<LocalDateTime, Double> resampledDataIP1 = resampleHourly(originalDataIP1);
	        Map<LocalDateTime, Double> resampledDataIP2 = resampleHourly(originalDataIP2);
	        Map<LocalDateTime, Double> resampledDataIP3 = resampleHourly(originalDataIP3);
	        //print
	        log.debug("Resampled data on an hourly basis OP1 : " + resampledDataOP1);
	        log.debug("Resampled data on an hourly basis OP2 : " + resampledDataOP2);
	        log.debug("Resampled data on an hourly basis OP3 : " + resampledDataOP3);
	        log.debug("Resampled data on an hourly basis IP1 : " + resampledDataIP1);
	        log.debug("Resampled data on an hourly basis IP2 : " + resampledDataIP2);
	        log.debug("Resampled data on an hourly basis IP3 : " + resampledDataIP3);
	        
	        
	        //processing vrm stats data
	        Map<LocalDateTime, Double> bs_map = stats_responce.has("bs")?sampledDstatsResponce(stats_responce.get("bs").toString()):sampledDstatsResponce("[]");
	        Map<LocalDateTime, Double> bv_map = stats_responce.has("bv")?sampledDstatsResponce(stats_responce.get("bv").toString()):sampledDstatsResponce("[]");
	        Map<LocalDateTime, Double> solar_yield_map = stats_responce.has("total_solar_yield")?sampledDstatsResponce(stats_responce.get("total_solar_yield").toString()):sampledDstatsResponce("[]"); 
	        Map<LocalDateTime, Double> consumption_map = stats_responce.has("total_consumption")?sampledDstatsResponce(stats_responce.get("total_consumption").toString()):sampledDstatsResponce("[]");
	        Map<LocalDateTime, Double> genset_map = stats_responce.has("total_genset")?sampledDstatsResponce(stats_responce.get("total_genset").toString()):sampledDstatsResponce("[]"); 
	        

	        
	        for (LocalDateTime element : timestampList) {
	            List<Double> tempAttributeValueList = new ArrayList<>();
	            tempAttributeValueList.add(resampledDataOP1.get(element) == null ? 0 : resampledDataOP1.get(element));
	            tempAttributeValueList.add(resampledDataOP2.get(element) == null ? 0 : resampledDataOP2.get(element));
	            tempAttributeValueList.add(resampledDataOP3.get(element) == null ? 0 : resampledDataOP3.get(element));
	            tempAttributeValueList.add(resampledDataIP1.get(element) == null ? 0 : resampledDataIP1.get(element));
	            tempAttributeValueList.add(resampledDataIP2.get(element) == null ? 0 : resampledDataIP2.get(element));
	            tempAttributeValueList.add(resampledDataIP3.get(element) == null ? 0 : resampledDataIP3.get(element));
	            
	            

	            //Calculate OPT and IPT(sum of all OPs and IPs)
	            Double OPT =0.00;
	            Double IPT =0.00;

	            OPT = (resampledDataOP1.get(element) == null ? 0 : resampledDataOP1.get(element))+
	                    (resampledDataOP2.get(element) == null ? 0 : resampledDataOP2.get(element))+
	                            (resampledDataOP3.get(element) == null ? 0 : resampledDataOP3.get(element));
	            IPT = (resampledDataIP1.get(element) == null ? 0 : resampledDataIP1.get(element))+
	                    (resampledDataIP2.get(element) == null ? 0 : resampledDataIP2.get(element))+
	                    (resampledDataIP3.get(element) == null ? 0 : resampledDataIP3.get(element));

	            tempAttributeValueList.add(OPT);
	            tempAttributeValueList.add(IPT);
	            //log.info("OPT: "+OPT);
	            //log.info("OPT: "+IPT);
                //===================new Calculation========================================================================================================
	            Double Load_Percentage_Solarator = (IPT / sol_gen_size)*100;
	            Double Load_Percentage_Comparision = (OPT / comp_gen_size)*100;
	            
	            Double Consumption_Solarator = 1.00;
	            if (Load_Percentage_Solarator<30) {
	            	Consumption_Solarator = sol_cons_load_25;
	            } else if (Load_Percentage_Solarator<60) {
	            	Consumption_Solarator = sol_cons_load_50;
	            } else if (Load_Percentage_Solarator<90) {
	            	Consumption_Solarator = sol_cons_load_75;
	            } else if (Load_Percentage_Solarator<105) {
	            	Consumption_Solarator = sol_cons_load_100;
	            } else if (Load_Percentage_Solarator>=105) {
	            	Consumption_Solarator = sol_cons_load_110;
	            } 
	            
	            Double Consumption_Comparision = 1.00;
	            if (Load_Percentage_Comparision<30) {
	            	Consumption_Comparision = com_cons_load_25;
	            } else if (Load_Percentage_Comparision<60) {
	            	Consumption_Comparision = com_cons_load_50;
	            } else if (Load_Percentage_Comparision<90) {
	            	Consumption_Comparision = com_cons_load_75;
	            } else if (Load_Percentage_Comparision<105) {
	            	Consumption_Comparision = com_cons_load_100;
	            } else if (Load_Percentage_Comparision>=105) {
	            	Consumption_Comparision = com_cons_load_110;
	            } 
	            
	            Double SolaratorCost = Consumption_Solarator * fuel_cost;
	            Double ComparisionCost = Consumption_Comparision * fuel_cost;
	            Double SolaratorCO2 = (gen_fuel_cost == null ? Consumption_Solarator * fuel_cost : Consumption_Solarator * gen_fuel_cost);
	            Double ComparisionCO2 = (gen_fuel_cost == null ? Consumption_Comparision * fuel_cost : Consumption_Comparision * gen_fuel_cost);
	            
	            tempAttributeValueList.add(Load_Percentage_Solarator);
	            tempAttributeValueList.add(Load_Percentage_Comparision);
	            tempAttributeValueList.add(Consumption_Solarator);
	            tempAttributeValueList.add(Consumption_Comparision);
	            tempAttributeValueList.add(SolaratorCost);
	            tempAttributeValueList.add(ComparisionCost);
	            tempAttributeValueList.add(SolaratorCO2);
	            tempAttributeValueList.add(ComparisionCO2);
	            
	            
	            tempAttributeValueList.add(bs_map.get(element) == null ? 0 : bs_map.get(element));
	            tempAttributeValueList.add(bv_map.get(element) == null ? 0 : bv_map.get(element));
	            tempAttributeValueList.add(solar_yield_map.get(element) == null ? 0 : solar_yield_map.get(element));
	            tempAttributeValueList.add(consumption_map.get(element) == null ? 0 : consumption_map.get(element));
	            tempAttributeValueList.add(genset_map.get(element) == null ? 0 : genset_map.get(element));
	            
	            //================================================================================================================================
	            //Insert the values to Database
	            try {
	            	
	            	boolean existTheEntry = checkSiteIdandTimestampIntheTable(element, context.getProperty("siteID").toString());
	            	if (!existTheEntry) {
	            		insertIntoMysql(element,tempAttributeValueList, context.getProperty("siteID").toString());
	            	} else {
	            	
	            		log.info("Timestamp and site id combination found for: " + context.getProperty("siteID").toString() + "-"+String.valueOf(element)+" Hence skip the insert");
	            	}
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					log.error("From insertIntoMysql Method: "+e);
				}
	        	}
	        insertLastRetrievedDataTimeIntoMysql(lastHour,context.getProperty("siteID").toString());
	        } catch (Exception e) {
	        	log.error(e);
		}
		return true;
	}
	
	/**Resample in hourly bases
	 *Store the data values with hour key
	 *return a LocalDateTime, Double map**/
	public static Map<LocalDateTime, Double> resampleHourly(List<DataPoint> data) {
        Map<LocalDateTime, Double> resampledData = new HashMap<>();

        // Group data points by the hour component of their timestamps
        Map<LocalDateTime, List<DataPoint>> hourlyData = new HashMap<>();
        for (DataPoint dp : data) {
            //int hour = dp.timestamp.getHour();
        	LocalDateTime hour = dp.timestamp.withMinute(0).withSecond(0);
            hourlyData.computeIfAbsent(hour, k -> new ArrayList<>()).add(dp);
        }

        // Aggregate data points within each hour
        for (LocalDateTime hour : hourlyData.keySet()) {
            List<DataPoint> hourData = hourlyData.get(hour);
            double sum = 0;
            for (DataPoint dp : hourData) {
                sum += dp.value;
            }
            double average = sum/hourData.size();
            //put the resampled data into map
            resampledData.put(hourData.get(0).timestamp.withMinute(0).withSecond(0), average); // put hour and sum to                                                                                            // the resamples data map
        }
        return resampledData;
    }
    
	/**Store the data values in hourly basis. 
	 *data input should have hourly values
	 *return a LocalDateTime, Double map**/
	public static Map<LocalDateTime, Double> sampledDstatsResponce(String data) {
    	Map<LocalDateTime, Double> originalData = new HashMap<>();
    	if (data != null && !data.isEmpty() && data.startsWith("[") && data.endsWith("]")) {
    		JSONArray jsonArray = new JSONArray(data);       	
            for (int i = 0; i < jsonArray.length(); i++) { //iteration of values of attribute codes such as op1,op2 etc
                JSONArray tempJsonArray = new JSONArray(jsonArray.get(i).toString());
                
                if (!JSONObject.NULL.equals(tempJsonArray.get(1)) && !JSONObject.NULL.equals(tempJsonArray.get(0))) {
                	String stringTimestampinmillis = tempJsonArray.get(0).toString();
                	String stringTimestamp = stringTimestampinmillis.substring(0, stringTimestampinmillis.length() - 3);//get the timestamp of the particular attribute id value
                    Double valueOfTheTimesatmp = Double.parseDouble(tempJsonArray.get(1).toString()); //get the value of the particular
                                                                                                        // attribute id value
                    long longTimestamp = Long.parseLong(stringTimestamp);//convert timestamp to long

                    LocalDateTime dateTime = LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(longTimestamp),
                            ZoneOffset.UTC
                    );// LocalDateTime of the timesatmp
                    
                    originalData.put(dateTime, valueOfTheTimesatmp);
                }              
            }            
        }   	
        return originalData;
    }
	/**insert a time related to last processed data. 
	 *this time will be used in the next scheduled task iteration
	 *to fetch the data from vrm apis**/
    public static void insertLastRetrievedDataTimeIntoMysql(LocalDateTime lastHour, String siteId) throws Exception {

    	String url = MySqlURL;
        String user = MySqlUser;
        String password = MySqlPass;        
        String InsertQuery = "UPDATE last_retrieveddatatime SET timestamp = ? WHERE site_id = ?";// SQL query to insert data

        try {

            Connection conn = DriverManager.getConnection(url, user, password);
            PreparedStatement preparedStatement = conn.prepareStatement(InsertQuery);
            conn.setAutoCommit(false);

            // Set parameters
            preparedStatement.setString(1, String.valueOf(lastHour.toEpochSecond(ZoneOffset.UTC)));
            preparedStatement.setString(2, siteId);
            
            // Execute the query
            preparedStatement.executeUpdate();
            log.info("UPDATE last_retrieveddatatime successfully with: " + String.valueOf(lastHour.toEpochSecond(ZoneOffset.UTC))+"-"+siteId);
            conn.commit();
            conn.close();

        } catch (SQLException ex) {
        	log.error(ex);
        }
    }
    
    /**Check whether the provided site ID and timestamp combination
     *in the blackstump_vrm_daily table.**/
    public static boolean checkSiteIdandTimestampIntheTable(LocalDateTime timestamp, String siteId) throws Exception {

    	String url = MySqlURL;
        String user = MySqlUser;
        String password = MySqlPass;

        // SQL query to insert data
        String InsertQuery = "SELECT * FROM blackstump_vrm_daily WHERE site_id = ? AND timestamp = ?";
        boolean result = false;

        try {

            Connection conn = DriverManager.getConnection(url, user, password);
            PreparedStatement preparedStatement = conn.prepareStatement(InsertQuery);
            conn.setAutoCommit(false);

            // Set parameters
            preparedStatement.setString(1, siteId);
            preparedStatement.setString(2, String.valueOf(timestamp));
                        
            // Execute the query
            ResultSet resultSet = preparedStatement.executeQuery();
            
            if (resultSet.next()) {
                
                result = true;
            } else {
                
            	result = false;
                
            }
            conn.commit();
            conn.close();
                      
        } catch (SQLException ex) {
        	log.error(ex);
        }
		return result;
    }
    
    /**Insert hourly data in to blackstump_vrm_daily**/
    public static void insertIntoMysql(LocalDateTime timestamp, List<Double> attributesValues, String siteId) throws Exception {

    	String url = MySqlURL;
        String user = MySqlUser;
        String password = MySqlPass;

        // SQL query to insert data
        String InsertQuery = MySqlInsertQuery;

        try {

            Connection conn = DriverManager.getConnection(url, user, password);
            PreparedStatement preparedStatement = conn.prepareStatement(InsertQuery);
            conn.setAutoCommit(false);

            // Set parameters
            preparedStatement.setString(1, String.valueOf(timestamp));
            preparedStatement.setString(2, siteId);
            preparedStatement.setString(3, "276");
            preparedStatement.setDouble(4, attributesValues.get(0));
            preparedStatement.setDouble(5, attributesValues.get(1));
            preparedStatement.setDouble(6, attributesValues.get(2));
            preparedStatement.setDouble(7, attributesValues.get(3));
            preparedStatement.setDouble(8, attributesValues.get(4));
            preparedStatement.setDouble(9, attributesValues.get(5));
            preparedStatement.setDouble(10, attributesValues.get(6));
            preparedStatement.setDouble(11, attributesValues.get(7));
            preparedStatement.setDouble(12, attributesValues.get(8));
            preparedStatement.setDouble(13, attributesValues.get(9));
            preparedStatement.setDouble(14, attributesValues.get(10));
            preparedStatement.setDouble(15, attributesValues.get(11));
            preparedStatement.setDouble(16, attributesValues.get(12));
            preparedStatement.setDouble(17, attributesValues.get(13));
            preparedStatement.setDouble(18, attributesValues.get(14));
            preparedStatement.setDouble(19, attributesValues.get(15));
            preparedStatement.setDouble(20, attributesValues.get(16));
            preparedStatement.setDouble(21, attributesValues.get(17));
            preparedStatement.setDouble(22, attributesValues.get(18));
            preparedStatement.setDouble(23, attributesValues.get(19));
            preparedStatement.setDouble(24, attributesValues.get(20));

            // Execute the query
            preparedStatement.executeUpdate();
            log.info("Data inserted successfully for: " + String.valueOf(timestamp) + " siteId: " + siteId);
            conn.commit();
            conn.close();

        } catch (SQLException ex) {
        	log.error(ex);
        }
    }
    
    /**This is to create new object 
     *to store LocalDateTime,double comination and
     *later process it in side resmpling logic**/
    static class DataPoint { //datapoint class
        LocalDateTime timestamp;
        double value;

        public DataPoint(LocalDateTime timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
    
    public void setMySqlURL(String URL) {
        MySqlURL=URL;
    }

    public String getMySqlURL() {
        return MySqlURL;
    }
    
    public void setMySqlUser(String User) {
    	MySqlUser=User;
    }

    public String getMySqlUser() {
        return MySqlURL;
    }
    
    public void setMySqlPass(String Pass) {
    	MySqlPass=Pass;
    }

    public String getMySqlPass() {
        return MySqlPass;
    }
    
    public void setInsertQuery(String query) {
    	MySqlInsertQuery=query;
    }

    public String getInsertQuery() {
        return MySqlInsertQuery;
    }
}
