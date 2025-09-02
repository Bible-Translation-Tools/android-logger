#Logger
An advanced logging library that provides support for writing logs to a file and catching global application exceptions.

##Installation
To use this library your Android project must be configured to use the JCenter or Maven Central repositories.

Add the following to your package dependencies and sync gradle.
```
implementation 'org.unfoldingword.tools:logger:2.3.1'
```

##Set up Global Exception Handler
If you want to use the global exception handler then you should register it when your app starts.

```
Logger.registerGlobalExceptionHandler(pathToStacktraceDirectory);
```

The argument is the directory path where you want stacktraces to be stored.

##Set up Logger
The logger contains three levels of log detail

* Info
* Warning
* Error

To begin using the logger you must configure it

```
Logger.configure(pathToLogFile, minimumAllowdLogLevel);
```

The first argument gives the path to the log file that will be written to. The second argument is the lower log level that will be processed.

##Usage
The Logger is a singleton so to use it you simply call one of it's static log methods

```
Logger.e(tag, message, throwable);
Logger.w(tag, message, throwable);
Logger.w(tag, message);
Logger.i(tag, message);
```

There are other public methods that allow you to retrieve a list of log objects, flush the log, or list stacktrace files.

```
List<LogEntry> logsEntries = <Logger.getLogEntries();
List<LogEntry> logsEntries = <Logger.getLogEntries();
Logger.flush();
```