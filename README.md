# Logger
An advanced logging library that provides support for writing logs to a file and catching global application exceptions.

## Supported Platforms
* Android
* Desktop (JVM)

## Installation
To use this library your Android project must be configured to use the Maven Central repositories.

Add the following to your package dependencies and sync Gradle.
```
implementation 'org.bibletranslationtools:logger:3.0.0'
```

## Set up Global Exception Handler
If you want to use the global exception handler then you should register it when your app starts.

```
Logger.registerGlobalExceptionHandler(pathToStacktraceDirectory);
```

The argument is the directory path where you want stacktraces to be stored.

## Set up Logger
The logger contains three levels of log detail

* Info
* Warning
* Error

To begin using the logger you must configure it

```
Logger.configure(pathToLogFile, minimumAllowdLogLevel);
```

The first argument gives the path to the log file that will be written to. The second argument is the lower log level that will be processed.

## Usage
The Logger is a singleton so to use it you simply call one of its static log methods

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

## Reporting Crashes and Bugs

Use `HttpReporter` to send crash and bug reports to any HTTP endpoint.

### Create a context

```kotlin
// Android
val context = application

// Desktop
val context = Context(versionName = "1.6.0", udid = "build-123")
```

### Choose an auth strategy

**Bearer token** (GitHub, GitLab, most REST APIs)
```kotlin
val reporter = HttpReporter.bearer(
    url = "https://api.github.com/repos/owner/repo/issues",
    token = "your-token",
    context = context
)
```

**API key header**
```kotlin
val reporter = HttpReporter.apiKey(
    url = "https://your-api.com/issues",
    headerName = "X-Api-Key",
    key = "your-key",
    context = context
)
```

**No auth** (internal endpoints)
```kotlin
val reporter = HttpReporter.noAuth(
    url = "https://internal.example.com/issues",
    context = context
)
```

**Custom** (multipart, URL tokens, multiple headers, etc.)
```kotlin
val reporter = HttpReporter(url, context) { title, body ->
    header("User-Agent", "MyApp/1.0")
    contentType(ContentType.MultiPart.FormData)
    setBody(MultiPartFormDataContent(formData {
        append("title", title)
        append("content", body)
        append("sender[email]", userEmail)
    }))
}
```

### Send reports

```kotlin
// Crash with stacktrace string
reporter.reportCrash(notes = "user description", stacktrace = stacktraceString)

// Crash with stacktrace file
reporter.reportCrash(notes = "user description", stacktraceFile = file, logFile = logFile)

// Bug report
reporter.reportBug(notes = "user description", log = logString)
reporter.reportBug(notes = "user description", logFile = logFile)
```