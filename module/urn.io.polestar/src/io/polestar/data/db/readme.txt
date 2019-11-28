All HDS schema types used in IPolestarPersistence.

LogQueryResult

<length>total number of records [integer]</length>
<filterLength>number of records when filter applied [integer]</filterLength>
<entry>
    <time>milliseconds from datum [long]</time>
    <level>log level [integer]</level>
    <origin>creator of message [string][nullable]</origin>
    <msg>log message [string]</msg>
</entry>

AuthenticationState

<authentication>
    <user>
        <username>username of a user [string]</username>
        <password>a password hash [string]</password>
        <role>admin|guest [string]</role>
    </user>
</authentication>

ScriptList

<scripts>
    <script>
        <id>script id in hexadecimal [string]</id>
        <order>the position of this script in list, the natural ordering [integer]</order>
        <name>the name of script</name>
        <period>a period in milliseconds serialised as string [string]</period>
        <public>public|private|secret|guest [string]</public>
        <triggers>
            <trigger>a sensor id for a sensor that will trigger this script to execute when it changes [string]</trigger>
        </triggers>
        <keywords>
            <keyword>[string]</keyword>
        </keywords>
    </script>
</scripts>

Script

<script>
    <id>script id in hexadecimal [string]</id>
    <order>the position of this script in list, the natural ordering [integer]</order>
    <name>the name of script</name>
    <period>a period in milliseconds serialised as string [string]</period>
    <public>public|private|secret|guest [string]</public>
    <triggers>comma separated list of triggers</triggers>
    <keywords>comma separated list of keywords</keywords>
</script>

ScriptStats

<scripts>
    <script>
        <id>unique id of script in hexadecimal [string]</id>
        <count>number of times script has executed [integer]</count>
        <errors>number of times script has thrown an exception [integer]</count>
        <errorPercent>percentage of time script has failed [float]</errorPercent>
        <lastExecTime>last time executed - milliseconds from datum [long] [nullable]</lastExecTime>
        <lastErrorTime>last time script failed - milliseconds from datum [long] [nullable]</lastErrorTime>
        <lastError>last error message [string] [nullable]</lastError>
        <lastEdited>last edit time - milliseconds from datum [long] [nullable]</lastEdited>
    </script>
</scripts>

SensorState

<sensors>
    <sensor>
        <id>sensor id [string]</id>
        <userError>error set by script [string]</userError>
        <valueError>error set by sensor value constraints [string]</valueError>
        <staleError>error set when not updated or changed [string]</staleError>
        <value>the current value of sensor [object]</value>
        <lastUpdated>time sensor last updated [long]</lastUpdated>
        <lastModified>time sensor value changed [long]</lastModified>
        <errorLastModified>time sensors error state last changed [long]</errorLastModified>
        <errorLastModifiedPublic>exposed time that sensors error state changed (maybe different if errorOnlyAfter configuration used) [long]</errorLastModifiedPublic>
    </sensor>
</sensors>

SensorErrorSummary

<sensor>
    <id>sensor id [string]</id>
    <errorPercent>percentage of time sensor has been in error [double]</errorPercent>
    <errorCount>number of times error has been raised [integer]</errorCount>
    <currentError>a current error message [string]</currentError>
    <lastError>the last error message [string]</lastError>
    <lastErrorRaisedRaw>time last error was raised (0 if never) [long]</lastErrorRaisedRaw>
    <lastErrorClearedRaw>time last error was cleared (0 if never, -1 if last error is still active) [long]</lastErrorClearedRaw>
    <lastErrorDurationRaw>duration of last error [long]</lastErrorDurationRaw>
    <errorDurationRaw>total duration during which errors where active [long]</errorDurationRaw>
</sensor>

SensorBackupRestoreStatus

<msg>current status message [string]</msg>
<progress>current progress [long]</progress>
<progressTotal>total progress to get to end [long]</progressTotal>

SensorBackupInfo

<backupInfo>
    <sensor>
        <id></id>
        <count>total number of sensor values [long]</count>
        <size>total size in bytes [long]</size>
    </sensor>
    <totals>
        <count>total number of values for all sensors [long]</count>
        <size>total size in bytes for all sensors [long]</size>
    </totals>
</backupInfo>

SensorRestoreInfo

<sensor>
    <id>sensor id [string]</id>
    <count>total number of sensor values [long]</count>
    <overlap>true if any values in restore overlap with existing data [boolean]</overlap>
</sensor>
<oldest>time of oldest value in restore file - milliseconds from datum [long]</oldest>
<newest>time of newest value in restore file - milliseconds from datum [long]</newest>
<count>total number sensor values across all sensors [long]</count>
