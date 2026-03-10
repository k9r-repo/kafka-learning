package io.github.kalpesh.ksql.udf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FraudScoreUDF
 * Tests all UDF functions with various scenarios including edge cases
 * 
 * @author Kalpesh Rawal
 */
@DisplayName("FraudScoreUDF Tests")
class FraudScoreUDFTest {

    private FraudScoreUDF udf;

    @BeforeEach
    void setUp() {
        udf = new FraudScoreUDF();
    }

    // ========================================================================
    // Z-Score Risk Tests
    // ========================================================================

    @Nested
    @DisplayName("calculateZScoreRisk() Tests")
    class ZScoreRiskTests {

        @Test
        @DisplayName("Should return 100 for z-score >= 5")
        void shouldReturnMaxScoreForHighZScore() {
            // User avg = 100, stddev = 20
            // Transaction = 200, z-score = (200-100)/20 = 5.0
            Integer score = udf.calculateZScoreRisk(200.0, 100.0, 20.0);
            assertThat(score).isEqualTo(100);
        }

        @Test
        @DisplayName("Should return 90 for z-score >= 4")
        void shouldReturn90ForZScore4() {
            // z-score = (180-100)/20 = 4.0
            Integer score = udf.calculateZScoreRisk(180.0, 100.0, 20.0);
            assertThat(score).isEqualTo(90);
        }

        @Test
        @DisplayName("Should return 75 for z-score >= 3")
        void shouldReturn75ForZScore3() {
            // z-score = (160-100)/20 = 3.0
            Integer score = udf.calculateZScoreRisk(160.0, 100.0, 20.0);
            assertThat(score).isEqualTo(75);
        }

        @Test
        @DisplayName("Should return 50 for z-score >= 2")
        void shouldReturn50ForZScore2() {
            // z-score = (140-100)/20 = 2.0
            Integer score = udf.calculateZScoreRisk(140.0, 100.0, 20.0);
            assertThat(score).isEqualTo(50);
        }

        @Test
        @DisplayName("Should return 25 for z-score < 2")
        void shouldReturn25ForLowZScore() {
            // z-score = (120-100)/20 = 1.0
            Integer score = udf.calculateZScoreRisk(120.0, 100.0, 20.0);
            assertThat(score).isEqualTo(25);
        }

        @Test
        @DisplayName("Should handle negative z-score (below average)")
        void shouldHandleNegativeZScore() {
            // z-score = (50-100)/20 = -2.5 (absolute value = 2.5)
            Integer score = udf.calculateZScoreRisk(50.0, 100.0, 20.0);
            assertThat(score).isEqualTo(50);
        }

        @Test
        @DisplayName("Should return 0 for null transaction amount")
        void shouldReturn0ForNullTransactionAmount() {
            Integer score = udf.calculateZScoreRisk(null, 100.0, 20.0);
            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return 0 for null user average")
        void shouldReturn0ForNullUserAverage() {
            Integer score = udf.calculateZScoreRisk(100.0, null, 20.0);
            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return 0 for null standard deviation")
        void shouldReturn0ForNullStdDev() {
            Integer score = udf.calculateZScoreRisk(100.0, 100.0, null);
            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle zero standard deviation")
        void shouldHandleZeroStdDev() {
            // When stddev = 0, fall back to ratio-based scoring
            // Transaction = 300, avg = 100, ratio = 3.0
            Integer score = udf.calculateZScoreRisk(300.0, 100.0, 0.0);
            assertThat(score).isEqualTo(75);
        }

        @Test
        @DisplayName("Should return 0 for zero or negative transaction amount")
        void shouldReturn0ForInvalidAmount() {
            Integer score1 = udf.calculateZScoreRisk(0.0, 100.0, 20.0);
            Integer score2 = udf.calculateZScoreRisk(-50.0, 100.0, 20.0);
            
            assertThat(score1).isEqualTo(0);
            assertThat(score2).isEqualTo(0);
        }
    }

    // ========================================================================
    // Velocity Risk Tests
    // ========================================================================

    @Nested
    @DisplayName("calculateVelocityRisk() Tests")
    class VelocityRiskTests {

        @Test
        @DisplayName("Should return 95+ for 2+ transactions per minute")
        void shouldReturnHighScoreForHighVelocity() {
            // 10 transactions in 5 minutes = 2 txn/min
            Integer score = udf.calculateVelocityRisk(10, 5, 1000.0);
            assertThat(score).isGreaterThanOrEqualTo(95);
        }

        @Test
        @DisplayName("Should return 85 for 1 transaction per minute")
        void shouldReturn85ForMediumHighVelocity() {
            // 5 transactions in 5 minutes = 1 txn/min
            Integer score = udf.calculateVelocityRisk(5, 5, 500.0);
            assertThat(score).isEqualTo(85);
        }

        @Test
        @DisplayName("Should return 70 for 0.5 transactions per minute")
        void shouldReturn70ForMediumVelocity() {
            // 5 transactions in 10 minutes = 0.5 txn/min
            Integer score = udf.calculateVelocityRisk(5, 10, 500.0);
            assertThat(score).isEqualTo(70);
        }

        @Test
        @DisplayName("Should increase score for high total amount")
        void shouldBoostScoreForHighAmount() {
            // Same velocity, but high total amount
            Integer normalScore = udf.calculateVelocityRisk(5, 10, 500.0);
            Integer boostedScore = udf.calculateVelocityRisk(5, 10, 6000.0);
            
            assertThat(boostedScore).isGreaterThan(normalScore);
        }

        @Test
        @DisplayName("Should increase score for high avg per transaction")
        void shouldBoostScoreForHighAvgTransaction() {
            // 3 transactions, $3600 total = $1200 per transaction
            Integer score = udf.calculateVelocityRisk(3, 5, 3600.0);
            // Base score for 0.6 txn/min = 55, +10 for amount > 2000, +5 for avg > 1000 = 70
            assertThat(score).isGreaterThanOrEqualTo(65);
        }

        @Test
        @DisplayName("Should return 0 for null inputs")
        void shouldReturn0ForNullInputs() {
            Integer score1 = udf.calculateVelocityRisk(null, 5, 100.0);
            Integer score2 = udf.calculateVelocityRisk(5, null, 100.0);
            Integer score3 = udf.calculateVelocityRisk(5, 5, null);
            
            assertThat(score1).isEqualTo(0);
            assertThat(score2).isEqualTo(0);
            assertThat(score3).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return 0 for invalid inputs")
        void shouldReturn0ForInvalidInputs() {
            Integer score1 = udf.calculateVelocityRisk(0, 5, 100.0);
            Integer score2 = udf.calculateVelocityRisk(5, 0, 100.0);
            Integer score3 = udf.calculateVelocityRisk(5, 5, 0.0);
            
            assertThat(score1).isEqualTo(0);
            assertThat(score2).isEqualTo(0);
            assertThat(score3).isEqualTo(0);
        }
    }

    // ========================================================================
    // Geographic Risk Tests
    // ========================================================================

    @Nested
    @DisplayName("calculateGeoRisk() Tests")
    class GeoRiskTests {

        @Test
        @DisplayName("Should return 100 for 4+ countries")
        void shouldReturn100ForManyCountries() {
            Integer score = udf.calculateGeoRisk(4, 5, 10);
            assertThat(score).isEqualTo(100);
        }

        @Test
        @DisplayName("Should return 95 for 3 countries")
        void shouldReturn95For3Countries() {
            // Use 10 hour window and < 5 transactions to avoid boosts
            Integer score = udf.calculateGeoRisk(3, 10, 3);
            assertThat(score).isEqualTo(95);
        }

        @Test
        @DisplayName("Should return 100 for different countries within 1 hour")
        void shouldReturn100ForShortTimeWindow() {
            // 2 countries within 1 hour = physically impossible
            Integer score = udf.calculateGeoRisk(2, 1, 2);
            assertThat(score).isEqualTo(100);
        }

        @Test
        @DisplayName("Should boost score for short time windows")
        void shouldBoostScoreForShortTime() {
            Integer longWindow = udf.calculateGeoRisk(2, 10, 2);
            Integer shortWindow = udf.calculateGeoRisk(2, 2, 2);
            
            assertThat(shortWindow).isGreaterThan(longWindow);
        }

        @Test
        @DisplayName("Should boost score for high transaction count")
        void shouldBoostScoreForHighTxnCount() {
            Integer lowCount = udf.calculateGeoRisk(2, 5, 2);
            Integer highCount = udf.calculateGeoRisk(2, 5, 6);
            
            assertThat(highCount).isGreaterThan(lowCount);
        }

        @Test
        @DisplayName("Should return 0 for single country")
        void shouldReturn0ForSingleCountry() {
            // 1 country is not anomalous
            Integer score = udf.calculateGeoRisk(1, 5, 5);
            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return 0 for null inputs")
        void shouldReturn0ForNullInputs() {
            Integer score1 = udf.calculateGeoRisk(null, 5, 5);
            Integer score2 = udf.calculateGeoRisk(2, null, 5);
            Integer score3 = udf.calculateGeoRisk(2, 5, null);
            
            assertThat(score1).isEqualTo(0);
            assertThat(score2).isEqualTo(0);
            assertThat(score3).isEqualTo(0);
        }
    }

    // ========================================================================
    // Merchant Risk Tests
    // ========================================================================

    @Nested
    @DisplayName("calculateMerchantRisk() Tests")
    class MerchantRiskTests {

        @Test
        @DisplayName("Should return 90 for crypto exchange")
        void shouldReturn90ForCrypto() {
            Integer score = udf.calculateMerchantRisk("crypto_exchange");
            assertThat(score).isEqualTo(90);
        }

        @Test
        @DisplayName("Should return 85 for gambling")
        void shouldReturn85ForGambling() {
            Integer score = udf.calculateMerchantRisk("gambling");
            assertThat(score).isEqualTo(85);
        }

        @Test
        @DisplayName("Should return 80 for money transfer")
        void shouldReturn80ForMoneyTransfer() {
            Integer score = udf.calculateMerchantRisk("money_transfer");
            assertThat(score).isEqualTo(80);
        }

        @Test
        @DisplayName("Should return 50 for electronics")
        void shouldReturn50ForElectronics() {
            Integer score = udf.calculateMerchantRisk("electronics");
            assertThat(score).isEqualTo(50);
        }

        @Test
        @DisplayName("Should return 20 for grocery")
        void shouldReturn20ForGrocery() {
            Integer score = udf.calculateMerchantRisk("grocery");
            assertThat(score).isEqualTo(20);
        }

        @Test
        @DisplayName("Should return 30 for unknown category")
        void shouldReturn30ForUnknownCategory() {
            Integer score = udf.calculateMerchantRisk("unknown_category");
            assertThat(score).isEqualTo(30);
        }

        @Test
        @DisplayName("Should handle case insensitivity")
        void shouldHandleCaseInsensitivity() {
            Integer score1 = udf.calculateMerchantRisk("CRYPTO_EXCHANGE");
            Integer score2 = udf.calculateMerchantRisk("Crypto_Exchange");
            Integer score3 = udf.calculateMerchantRisk("crypto_exchange");
            
            assertThat(score1).isEqualTo(90);
            assertThat(score2).isEqualTo(90);
            assertThat(score3).isEqualTo(90);
        }

        @Test
        @DisplayName("Should return 0 for null or empty category")
        void shouldReturn0ForNullOrEmpty() {
            Integer score1 = udf.calculateMerchantRisk(null);
            Integer score2 = udf.calculateMerchantRisk("");
            Integer score3 = udf.calculateMerchantRisk("   ");
            
            assertThat(score1).isEqualTo(0);
            assertThat(score2).isEqualTo(0);
            assertThat(score3).isEqualTo(0);
        }
    }

    // ========================================================================
    // Comprehensive Score Tests
    // ========================================================================

    @Nested
    @DisplayName("calculateComprehensiveScore() Tests")
    class ComprehensiveScoreTests {

        @Test
        @DisplayName("Should calculate weighted average of risk factors")
        void shouldCalculateWeightedAverage() {
            // Weights: z-score=30%, velocity=30%, geo=25%, merchant=15%
            // 80*0.30 + 70*0.30 + 60*0.25 + 50*0.15 = 24 + 21 + 15 + 7.5 = 67.5 = 68
            Integer score = udf.calculateComprehensiveScore(80, 70, 60, 50);
            assertThat(score).isEqualTo(68);
        }

        @Test
        @DisplayName("Should boost score if any factor is critical (>= 90)")
        void shouldBoostScoreForCriticalFactor() {
            // With critical factor (95)
            Integer criticalScore = udf.calculateComprehensiveScore(95, 50, 50, 50);
            // Should be at least 85 due to critical factor boost
            assertThat(criticalScore).isGreaterThanOrEqualTo(85);
        }

        @Test
        @DisplayName("Should boost score if 3+ factors are high risk (>= 75)")
        void shouldBoostScoreForMultipleHighRiskFactors() {
            // 3 high-risk factors
            Integer score = udf.calculateComprehensiveScore(80, 80, 80, 60);
            // Base: 80*0.3 + 80*0.3 + 80*0.25 + 60*0.15 = 77, +10 for 3 high = 87
            assertThat(score).isGreaterThanOrEqualTo(85);
        }

        @Test
        @DisplayName("Should boost score if 2 factors are high risk")
        void shouldBoostScoreForTwoHighRiskFactors() {
            // 2 high-risk factors
            Integer normalScore = udf.calculateComprehensiveScore(50, 50, 50, 50);
            Integer boostedScore = udf.calculateComprehensiveScore(80, 80, 50, 50);
            
            assertThat(boostedScore).isGreaterThan(normalScore);
        }

        @Test
        @DisplayName("Should treat null inputs as zero risk")
        void shouldTreatNullAsZero() {
            Integer score = udf.calculateComprehensiveScore(null, 80, null, 60);
            // 0*0.3 + 80*0.3 + 0*0.25 + 60*0.15 = 33
            assertThat(score).isEqualTo(33);
        }

        @Test
        @DisplayName("Should never exceed 100")
        void shouldNeverExceed100() {
            Integer score = udf.calculateComprehensiveScore(100, 100, 100, 100);
            assertThat(score).isLessThanOrEqualTo(100);
        }

        @Test
        @DisplayName("Should return low score for all low risks")
        void shouldReturnLowScoreForAllLowRisks() {
            Integer score = udf.calculateComprehensiveScore(20, 20, 20, 20);
            assertThat(score).isEqualTo(20);
        }
    }

    // ========================================================================
    // Integration Tests
    // ========================================================================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Scenario: Normal transaction should have low score")
        void normalTransactionScenario() {
            // Normal transaction: $120 when user avg is $100, stddev is $30
            Integer zScore = udf.calculateZScoreRisk(120.0, 100.0, 30.0);
            Integer velocity = udf.calculateVelocityRisk(1, 60, 120.0);
            Integer geo = 0; // Same country
            Integer merchant = udf.calculateMerchantRisk("grocery");
            
            Integer comprehensive = udf.calculateComprehensiveScore(zScore, velocity, geo, merchant);
            
            // Should be low risk (< 40)
            assertThat(comprehensive).isLessThan(40);
        }

        @Test
        @DisplayName("Scenario: High-value crypto transaction should have high score")
        void fraudulentTransactionScenario() {
            // Fraudulent: $5000 when user avg is $100, stddev is $50
            Integer zScore = udf.calculateZScoreRisk(5000.0, 100.0, 50.0);
            Integer velocity = udf.calculateVelocityRisk(5, 5, 5000.0);
            Integer geo = udf.calculateGeoRisk(2, 1, 2);
            Integer merchant = udf.calculateMerchantRisk("crypto_exchange");
            
            Integer comprehensive = udf.calculateComprehensiveScore(zScore, velocity, geo, merchant);
            
            // Should be high risk (> 85)
            assertThat(comprehensive).isGreaterThan(85);
        }

        @Test
        @DisplayName("Scenario: Velocity fraud with normal amount")
        void velocityFraudScenario() {
            // Multiple small transactions rapidly
            Integer zScore = udf.calculateZScoreRisk(50.0, 100.0, 30.0);
            Integer velocity = udf.calculateVelocityRisk(10, 3, 500.0);
            Integer geo = 0;
            Integer merchant = udf.calculateMerchantRisk("restaurant");
            
            Integer comprehensive = udf.calculateComprehensiveScore(zScore, velocity, geo, merchant);
            
            // Should be medium-high risk (60-80) due to velocity
            assertThat(comprehensive).isBetween(50, 85);
        }
    }
}
