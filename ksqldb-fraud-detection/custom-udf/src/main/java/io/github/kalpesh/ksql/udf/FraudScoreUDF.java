package io.github.kalpesh.ksql.udf;

import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;

/**
 * Custom KSQLDB User-Defined Functions (UDF) for Fraud Detection
 * 
 * This UDF provides advanced fraud scoring algorithms based on multiple factors:
 * - Statistical deviation from user's spending patterns
 * - Transaction velocity and frequency
 * - Geographic risk factors
 * - Merchant category risk assessment
 * 
 * @author Kalpesh Rawal
 * @version 1.0.0
 */
@UdfDescription(
    name = "fraud_score",
    description = "Calculates a comprehensive fraud risk score (0-100) for a transaction",
    author = "Kalpesh Rawal",
    version = "1.0.0"
)
public class FraudScoreUDF {

    /**
     * Calculate fraud score based on statistical deviation (z-score)
     * 
     * The z-score indicates how many standard deviations a transaction amount
     * is from the user's average transaction amount.
     * 
     * Scoring logic:
     * - z-score >= 5: Critical risk (100)
     * - z-score >= 4: Very high risk (90)
     * - z-score >= 3: High risk (75)
     * - z-score >= 2: Medium risk (50)
     * - z-score < 2: Low risk (25)
     * 
     * @param transactionAmount The current transaction amount
     * @param userAvgAmount User's historical average transaction amount
     * @param userStdDev User's historical standard deviation
     * @return Fraud score from 0 to 100
     */
    @Udf(description = "Calculate fraud score based on statistical deviation (z-score)")
    public Integer calculateZScoreRisk(
            @UdfParameter(value = "transaction_amount", description = "Current transaction amount") 
            Double transactionAmount,
            
            @UdfParameter(value = "user_avg_amount", description = "User's average transaction amount") 
            Double userAvgAmount,
            
            @UdfParameter(value = "user_std_dev", description = "User's standard deviation") 
            Double userStdDev) {
        
        // Handle null inputs
        if (transactionAmount == null || userAvgAmount == null || userStdDev == null) {
            return 0;
        }
        
        // Handle zero or negative values
        if (transactionAmount <= 0 || userAvgAmount <= 0) {
            return 0;
        }
        
        // Avoid division by zero
        if (userStdDev == 0) {
            // If no deviation history, compare directly to average
            double ratio = transactionAmount / userAvgAmount;
            if (ratio >= 3.0) return 75;
            if (ratio >= 2.0) return 50;
            return 25;
        }
        
        // Calculate z-score
        double zScore = Math.abs((transactionAmount - userAvgAmount) / userStdDev);
        
        // Map z-score to fraud risk score
        if (zScore >= 5.0) return 100;
        if (zScore >= 4.0) return 90;
        if (zScore >= 3.0) return 75;
        if (zScore >= 2.0) return 50;
        
        return 25;
    }

    /**
     * Calculate fraud score for velocity-based fraud patterns
     * 
     * Velocity fraud is detected when a card has an unusually high number
     * of transactions in a short time period.
     * 
     * @param transactionCount Number of transactions in the time window
     * @param timeWindowMinutes Size of the time window in minutes
     * @param totalAmount Total amount spent in the window
     * @return Fraud score from 0 to 100
     */
    @Udf(description = "Calculate fraud score for velocity-based patterns")
    public Integer calculateVelocityRisk(
            @UdfParameter(value = "transaction_count", description = "Number of transactions in window") 
            Integer transactionCount,
            
            @UdfParameter(value = "time_window_minutes", description = "Time window size in minutes") 
            Integer timeWindowMinutes,
            
            @UdfParameter(value = "total_amount", description = "Total amount in window") 
            Double totalAmount) {
        
        // Handle null inputs
        if (transactionCount == null || timeWindowMinutes == null || totalAmount == null) {
            return 0;
        }
        
        // Handle invalid values
        if (transactionCount <= 0 || timeWindowMinutes <= 0 || totalAmount <= 0) {
            return 0;
        }
        
        // Calculate transactions per minute
        double txnPerMinute = (double) transactionCount / timeWindowMinutes;
        
        // Calculate average amount per transaction
        double avgPerTxn = totalAmount / transactionCount;
        
        // Base score on transaction frequency
        int baseScore;
        if (txnPerMinute >= 2.0) {
            baseScore = 95; // 2+ transactions per minute is highly suspicious
        } else if (txnPerMinute >= 1.0) {
            baseScore = 85;
        } else if (txnPerMinute >= 0.5) {
            baseScore = 70;
        } else if (txnPerMinute >= 0.2) {
            baseScore = 55;
        } else {
            baseScore = 30;
        }
        
        // Adjust score based on total amount
        if (totalAmount > 5000) {
            baseScore = Math.min(100, baseScore + 10);
        } else if (totalAmount > 2000) {
            baseScore = Math.min(100, baseScore + 5);
        }
        
        // Adjust score based on average transaction size
        if (avgPerTxn > 1000) {
            baseScore = Math.min(100, baseScore + 5);
        }
        
        return baseScore;
    }

    /**
     * Calculate fraud score for geographic anomalies
     * 
     * Detects impossible travel patterns (transactions from different countries
     * in a short time period) and assigns risk scores.
     * 
     * @param uniqueCountries Number of unique countries in time window
     * @param timeWindowHours Time window size in hours
     * @param transactionCount Total number of transactions
     * @return Fraud score from 0 to 100
     */
    @Udf(description = "Calculate fraud score for geographic anomalies")
    public Integer calculateGeoRisk(
            @UdfParameter(value = "unique_countries", description = "Number of unique countries") 
            Integer uniqueCountries,
            
            @UdfParameter(value = "time_window_hours", description = "Time window in hours") 
            Integer timeWindowHours,
            
            @UdfParameter(value = "transaction_count", description = "Number of transactions") 
            Integer transactionCount) {
        
        // Handle null inputs
        if (uniqueCountries == null || timeWindowHours == null || transactionCount == null) {
            return 0;
        }
        
        // Handle invalid values
        if (uniqueCountries <= 1 || timeWindowHours <= 0 || transactionCount <= 0) {
            return 0; // Single country is not anomalous
        }
        
        // Base score on number of countries
        int baseScore;
        if (uniqueCountries >= 4) {
            baseScore = 100; // 4+ countries in any timeframe is impossible
        } else if (uniqueCountries == 3) {
            baseScore = 95;
        } else {
            baseScore = 85; // 2 countries
        }
        
        // Adjust based on time window (shorter = more suspicious)
        if (timeWindowHours <= 1) {
            baseScore = 100; // Different countries within 1 hour is impossible
        } else if (timeWindowHours <= 3) {
            baseScore = Math.min(100, baseScore + 10);
        } else if (timeWindowHours <= 6) {
            baseScore = Math.min(100, baseScore + 5);
        }
        
        // Adjust based on transaction count
        if (transactionCount >= 5) {
            baseScore = Math.min(100, baseScore + 5);
        }
        
        return baseScore;
    }

    /**
     * Calculate comprehensive fraud score combining multiple risk factors
     * 
     * This is a multi-factor fraud scoring function that considers:
     * - Statistical deviation (z-score)
     * - Transaction velocity
     * - Geographic risk
     * - Merchant category risk
     * 
     * @param zScoreRisk Risk score from z-score analysis (0-100)
     * @param velocityRisk Risk score from velocity analysis (0-100)
     * @param geoRisk Risk score from geographic analysis (0-100)
     * @param merchantRisk Risk score from merchant category (0-100)
     * @return Combined fraud score from 0 to 100
     */
    @Udf(description = "Calculate comprehensive fraud score from multiple risk factors")
    public Integer calculateComprehensiveScore(
            @UdfParameter(value = "zscore_risk", description = "Z-score risk (0-100)") 
            Integer zScoreRisk,
            
            @UdfParameter(value = "velocity_risk", description = "Velocity risk (0-100)") 
            Integer velocityRisk,
            
            @UdfParameter(value = "geo_risk", description = "Geographic risk (0-100)") 
            Integer geoRisk,
            
            @UdfParameter(value = "merchant_risk", description = "Merchant category risk (0-100)") 
            Integer merchantRisk) {
        
        // Handle nulls by treating them as zero risk
        int zScore = (zScoreRisk != null) ? zScoreRisk : 0;
        int velocity = (velocityRisk != null) ? velocityRisk : 0;
        int geo = (geoRisk != null) ? geoRisk : 0;
        int merchant = (merchantRisk != null) ? merchantRisk : 0;
        
        // Weighted average of risk factors
        // Weights: z-score=30%, velocity=30%, geo=25%, merchant=15%
        double weightedScore = (zScore * 0.30) + (velocity * 0.30) + (geo * 0.25) + (merchant * 0.15);
        
        // If any single factor is critical (>= 90), boost the overall score
        int maxRisk = Math.max(Math.max(zScore, velocity), Math.max(geo, merchant));
        if (maxRisk >= 90) {
            weightedScore = Math.max(weightedScore, 85.0);
        }
        
        // If multiple factors are high risk, boost further
        int highRiskCount = 0;
        if (zScore >= 75) highRiskCount++;
        if (velocity >= 75) highRiskCount++;
        if (geo >= 75) highRiskCount++;
        if (merchant >= 75) highRiskCount++;
        
        if (highRiskCount >= 3) {
            weightedScore = Math.min(100, weightedScore + 10);
        } else if (highRiskCount == 2) {
            weightedScore = Math.min(100, weightedScore + 5);
        }
        
        return (int) Math.round(Math.min(100, weightedScore));
    }

    /**
     * Simple merchant category risk scorer
     * 
     * @param merchantCategory The merchant category string
     * @return Risk score from 0 to 100
     */
    @Udf(description = "Calculate risk score for merchant category")
    public Integer calculateMerchantRisk(
            @UdfParameter(value = "merchant_category", description = "Merchant category name") 
            String merchantCategory) {
        
        if (merchantCategory == null || merchantCategory.trim().isEmpty()) {
            return 0;
        }
        
        String category = merchantCategory.toLowerCase().trim();
        
        // High-risk categories
        if (category.equals("crypto_exchange") || category.equals("cryptocurrency")) {
            return 90;
        }
        if (category.equals("gambling") || category.equals("casino")) {
            return 85;
        }
        if (category.equals("money_transfer") || category.equals("wire_transfer")) {
            return 80;
        }
        
        // Medium-risk categories
        if (category.equals("electronics") || category.equals("jewelry")) {
            return 50;
        }
        if (category.equals("online_shopping") || category.equals("ecommerce")) {
            return 40;
        }
        
        // Low-risk categories
        if (category.equals("grocery") || category.equals("gas_station") || 
            category.equals("restaurant") || category.equals("pharmacy")) {
            return 20;
        }
        
        // Default for unknown categories
        return 30;
    }
}
