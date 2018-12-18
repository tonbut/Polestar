package io.polestar.api;

/** Types of query that can be performed */
public enum QueryType {
	/** Numeric difference between first and last values */
    DIFF,
    /** Sum all values */
    SUM,
    /** Average of values */
    AVERAGE,
    /** running total of all values */
    RUNNING_TOTAL,
    /** Maximum value */
    MAX,
    /** Minimum value */
    MIN,
    /** Determine percentile (value where percentage of time value is below, 0=min, 0.5=median, 1.0=max) - specify IPolestarQuery.setQueryParameter() */
    PERCENTILE,
    /** Number of value changes*/
    COUNT,
    /** Standard deviation from average */
    STDDEV,
    /** Discrete value with longest duration */
    DISCRETE_MOST,
    /** Diff but negative values are capped at zero */
    POSITIVE_DIFF,
    /** count number of rising edges */
    BOOLEAN_RISING_EDGE_COUNT,
    /** count number of falling edges */
    BOOLEAN_FALLING_EDGE_COUNT,
    /** return boolean input but attempt to show changes */
    BOOLEAN_CHANGE,
    /** smoothed 360 wrapping 360->1 degree is small rotation */
    ROTATION_360_AVERAGE,
    /** First value */
    SAMPLE,
    /** rate of change over sample period -diff/time **/
    RATE_OF_CHANGE,
    
    /** Time last modified */
    LAST_MODIFIED, LAST_MODIFIED_RELATIVE,
    /** Last value */
    LAST_VALUE,
    /** Last time value equals value specified by IPolestarQuery.setQueryParameter() */
    LAST_EQUALS_TIME, LAST_EQUALS_TIME_RELATIVE,
    /** Last time value less than value specified by IPolestarQuery.setQueryParameter() */
    LAST_LESS_THAN_TIME, LAST_LESS_THAN_TIME_RELATIVE,
    /** Last time value greater than value specified by IPolestarQuery.setQueryParameter() */
    LAST_GREATER_THAN_TIME, LAST_GREATER_THAN_TIME_RELATIVE,
    /** Last time value matched by matcher specified by IPolestarQuery.setQueryMatcher() */
    LAST_MATCH_TIME,
    /** Last time, relative to start value matched by matcher specified by IPolestarQuery.setQueryMatcher() */
    LAST_MATCH_TIME_RELATIVE,
    /** Last value matched by matcher specified by IPolestarQuery.setQueryMatcher() */
    LAST_MATCH_VALUE,
    
    /** First time modified */
    FIRST_MODIFIED, FIRST_MODIFIED_RELATIVE,
    /** First value */
    FIRST_VALUE,
    /** First time equals value specified by IPolestarQuery.setQueryParameter() */
    FIRST_EQUALS_TIME, FIRST_EQUALS_TIME_RELATIVE,
    /** First time value less than value specified by IPolestarQuery.setQueryParameter() */
    FIRST_LESS_THAN_TIME, FIRST_LESS_THAN_TIME_RELATIVE,
    /** First time value greater than value specified by IPolestarQuery.setQueryParameter() */
    FIRST_GREATER_THAN_TIME,FIRST_GREATER_THAN_TIME_RELATIVE,
    /** First time value matched by matcher specified by IPolestarQuery.setQueryMatcher() */
    FIRST_MATCH_TIME, FIRST_MATCH_TIME_RELATIVE,

    /** First value matched by matcher specified by IPolestarQuery.setQueryMatcher() */
    FIRST_MATCH_VALUE,
    
    /* Durations that a condition is true */
    
    /** Duration that value equals value specified by IPolestarQuery.setQueryParameter() */
    DURATION_EQUALS,
    /** Duration that value is less that value specified by IPolestarQuery.setQueryParameter() */
    DURATION_LESS_THAN,
    /** Duration that value is greater than value specified by IPolestarQuery.setQueryParameter() */
    DURATION_GREATER_THAN,
    /** Duration that value is matched by matcher specified by IPolestarQuery.setQueryMatcher() */
    DURATION_MATCHES
}