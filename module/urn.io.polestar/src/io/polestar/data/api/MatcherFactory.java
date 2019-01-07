package io.polestar.data.api;

import io.polestar.api.IPolestarMatcher;

public abstract class MatcherFactory
{
	public static IPolestarMatcher getEqualsInstance(final Object mValue)
	{	return new IPolestarMatcher()
		{	@Override
			public boolean matches(Object aValue, long aTimestamp)
			{	return mValue.equals(aValue);
			}
		};
	}
	
	public static IPolestarMatcher getLessThanInstance(final Object aValue)
	{	return new IPolestarMatcher()
		{	private final double mValue;
			{	mValue=((Number)aValue).doubleValue();
			}
			
			@Override
			public boolean matches(Object aValue, long aTimestamp)
			{	if (aValue instanceof Number)
				{	double v=((Number)aValue).doubleValue();
					return v<mValue;
				}
				else
				{	return false;
				}
			}
		};
	}
	
	public static IPolestarMatcher getGreaterThanInstance(final Object aValue)
	{	return new IPolestarMatcher()
		{	private final double mValue;
			{	mValue=((Number)aValue).doubleValue();
			}
			
			@Override
			public boolean matches(Object aValue, long aTimestamp)
			{	if (aValue instanceof Number)
				{	double v=((Number)aValue).doubleValue();
					return v>mValue;
				}
				else
				{	return false;
				}
			}
		};
	}

}
