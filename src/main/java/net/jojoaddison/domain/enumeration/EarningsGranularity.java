package net.jojoaddison.domain.enumeration;

/**
 * The bucket size an earnings series is reported in.
 *
 * <p>Daily, weekly and monthly are the three the console offers, because they are the three a wage
 * bill is actually paid on.
 */
public enum EarningsGranularity {
    DAILY,
    WEEKLY,
    MONTHLY,
}
