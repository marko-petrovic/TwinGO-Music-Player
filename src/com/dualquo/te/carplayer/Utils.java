package com.dualquo.te.carplayer;

import android.annotation.SuppressLint;

public class Utils 
{
	//haversineDistance formula
		//calculation of distance between two gps points using Haversine formula
		//http://en.wikipedia.org/wiki/Haversine_formula
		@SuppressLint("UseValueOf")
		public static float haversineDistance(float lat1, float lng1, float lat2, float lng2) 
		{
		    double earthRadius = 3958.75;
		    
		    double dLat = Math.toRadians(lat2-lat1);
		    double dLng = Math.toRadians(lng2-lng1);
		    
		    double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
		               Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
		               Math.sin(dLng/2) * Math.sin(dLng/2);
		    
		    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
		    
		    double dist = earthRadius * c;

		    int meterConversion = 1609;

		    //result returns in kilometers
		    return new Float((dist * meterConversion)/1000).floatValue();
		}
		
		public static double haversineDistanceDouble(double lat1, double lng1, double lat2, double lng2) 
		{
		    double earthRadius = 3958.75;
		    
		    double dLat = Math.toRadians(lat2-lat1);
		    double dLng = Math.toRadians(lng2-lng1);
		    
		    double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
		               Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
		               Math.sin(dLng/2) * Math.sin(dLng/2);
		    
		    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
		    
		    double dist = earthRadius * c;

		    int meterConversion = 1609;

		    double result = (dist * meterConversion)/1000;
		    
		    //result returns in kilometers
		    return result;
		}
}
