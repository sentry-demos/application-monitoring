from datetime import datetime
import numpy
from pytz import timezone
import time
from random import choices

def release():
    d=datetime.today()
    week=str((d.day-1)//7+1)
    date_given = datetime.today().date()
    month = str(date_given.month)
    return month + "." + week

# The hour is either '14' or '12' which allows for a nice overlap to show a high-to-low or low-to-high change
# the 'condition' is either "greater than or equal to" or "less than or equal to"
# The delay value in seconds is according to a logarithmic distribution of 1 to 10"
def wait(condition, hour, number):
    current_hour = datetime.now(timezone('America/Los_Angeles')).hour
    time_to_sleep = numpy.random.lognormal(0.75, .6, 1)[0] if condition(current_hour, hour)  else numpy.random.lognormal(1.5, .5, 1)[0]
    time.sleep(time_to_sleep + number)


# https://www.postgresql.org/docs/9.0/functions-datetime.html
# 'n' seconds input for pg_sleep, but actual sleep time ends up being much longer
times = [0.05, 0.25, 0.5, .75, 1.0, 1.25]
# weight distribution that favors faster times
weights1 = [0.16, 0.5, 0.14, 0.1, 0.06, 0.04]
# weight distribution that favors the slower times
weights2 = [0.04, 0.06, 0.1, 0.14, 0.5, 0.16]

def weighter(condition, hour):
    current_hour = datetime.now(timezone('America/Los_Angeles')).hour
    time_to_sleep = choices(times, weights1) if condition(current_hour, hour)  else choices(times, weights2)
    return time_to_sleep[0]

def parseHeaders(keys, headers):
    parsedHeaders = {}
    for key in keys:
        value = headers.get(key) if headers.get(key) != "undefined" else None
        parsedHeaders[key] = value
    return parsedHeaders