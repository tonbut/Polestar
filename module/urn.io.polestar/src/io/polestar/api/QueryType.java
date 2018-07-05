package io.polestar.api;

/** Types of query that can be performed */
public enum QueryType {
	/** Numeric difference between first and last values */
    DIFF,
    /** Sum all values */
    SUM,
    /** Average of values */
    AVERAGE,
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
    
    /** Time last modified */
    LAST_MODIFIED,
    /** Last value */
    LAST_VALUE,
    /** Last time value equals value specified by IPolestarQuery.setQueryParameter() */
    LAST_EQUALS_TIME,
    /** Last time value less than value specified by IPolestarQuery.setQueryParameter() */
    LAST_LESS_THAN_TIME,
    /** Last time value greater than value specified by IPolestarQuery.setQueryParameter() */
    LAST_GREATER_THAN_TIME,
    /** Last time value matched by matcher specified by IPolestarQuery.setQueryMatcher() */
    LAST_MATCH_TIME,
    /** Last value matched by matcher specified by IPolestarQuery.setQueryMatcher() */
    LAST_MATCH_VALUE,
    
    /** First time modified */
    FIRST_MODIFIED,
    /** First value */
    FIRST_VALUE,
    /** First time equals value specified by IPolestarQuery.setQueryParameter() */
    FIRST_EQUALS_TIME,
    /** First time value less than value specified by IPolestarQuery.setQueryParameter() */
    FIRST_LESS_THAN_TIME,
    /** First time value greater than value specified by IPolestarQuery.setQueryParameter() */
    FIRST_GREATER_THAN_TIME,
    /** First time value matched by matcher specified by IPolestarQuery.setQueryMatcher() */
    FIRST_MATCH_TIME,
    /** First value matched by matcher specified by IPolestarQuery.setQueryMatcher() */
    FIRST_MATCH_VALUE,
    
    /** Duration value equals value specified by IPolestarQuery.setQueryParameter() */
    DURATION_EQUALS,
    /** Duration value less that value specified by IPolestarQuery.setQueryParameter() */
    DURATION_LESS_THAN,
    /** Duration value greater than value specified by IPolestarQuery.setQueryParameter() */
    DURATION_GREATER_THAN,
    /** Duration value matched by matcher specified by IPolestarQuery.setQueryMatcher() */
    DURATION_MATCHES
}