#!/bin/bash

#check starting
APP_DIR=$(dirname $(dirname $(readlink -f $0)))
timestamp=`date +%s`
starting="${APP_DIR}/starting"

if [ ! -f "$starting" ]; then
    echo -e "[WARN] instance not starting..."
    exit 0
else
    latest_start_timestamp=`tail -1 $starting`
    if [ $(($timestamp-$latest_start_timestamp)) -le 30 ]; then
        echo -e "[INFO] starting... latest time:${latest_start_timestamp}"
        exit 0
    fi
fi

cd $(dirname $0)

ports[0]=$EM_PORT
ports[1]=$tcp

#the varable for total_check_times and time_interval
basic_retry_times=2
basic_time_interval=4

function function_check()
{
    local function_check=$1
    local total_retry_times=$2
    local time_interval=$3
    local service_status=$4
    local check_result=0
    local retry_time=0

    while (( 1 ))
    do
    	if [ $retry_time -ge $total_retry_times ];then
	    echo -e "[ERROR] Check Retry too many times!"
            return 1
    	fi
    	${function_check} ${service_status}
        eval check_result=$?
    	if [ $check_result -eq 0 ];then
    	    return 0
    	elif [ $check_result -eq 1 ]; then
    	    echo "Waiting.........."
    	fi
    	sleep $time_interval && let "retry_time += 1"
    done
	return 0
}

function service_check()
{
	local check=$1
	if [ "${check}" == "start" ];then
	    function_check check_port ${basic_retry_times} ${basic_time_interval} start && function_check check_health ${basic_retry_times} ${basic_time_interval}
	    eval result=$?
	elif [ "${check}" == "stop" ];then
	    function_check check_port ${basic_retry_times} ${basic_time_interval} stop
	    eval result=$?
	fi
	if [ $result -eq 0 ];then
	    echo -e "[INFO] ${check} the $EM_APP SUCCESS"
	    return 0
	else
	    echo -e "[ERROR] ${check} the $EM_APP FAIL"
	    return 1
	fi
}

function check_health(){
    return 0
}

function check_port()
{
    check_status=$1
    for port in ${ports[@]}
    do
		/usr/sbin/ss -nl  2>/dev/null |grep -v 'Send-Q'|awk '{print $3}'|awk -F':' '{print $NF}'|grep -w "${port}"  &>/dev/null
        if [ $? -ne 0 ];then
            echo "[INFO] The ${port} is CLOSED"
	        if [ "${check_status}" == "start" ];then
	            return 1
	        else
	            continue
	        fi
	    else
	        echo "[INFO] The ${port} is OPENED"
	        if [ "${check_status}" == "stop" ];then
		        return 1
	        else
		        continue
	        fi
	    fi
    done
    return 0
}

case C"$1" in
    Cstart)
        service_check start
        if [ $? -eq 0 ];then
            exit 0
        else
            echo -e "[START_CHECK] ERROR, Not Started!"
            exit 1
        fi
        ;;
    Cstop)
        service_check stop
        if [ $? -eq 0 ];then
            exit 0
        else
            echo -e "[STOP_CHECK] ERROR, Not Stoped!"
            exit 1
        fi
        ;;
    C*)
        echo "Usage: $0 {start|stop} [INSTANCE_ID]"
		echo "Exam:  $0 start 1"
        ;;
esac