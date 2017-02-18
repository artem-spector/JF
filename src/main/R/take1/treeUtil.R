readThreadMetadata <- function(file) {
  lines <- readLines(file, warn = FALSE)
  frame <- fromJSON(lines)
  res <- list()
  for (i in 1:nrow(frame)) {
    threadId <- frame[i, "dumpId"]
    trace <- frame[i, "stackTrace"][[1]]
    if (nrow(trace) > 0) {
      res[[threadId]] <- stacktraceAsTree(trace)
    }
  }
  res
}

stacktraceAsTree <- function(stacktrace) {
  root <- createMethodNode(stacktrace[nrow(stacktrace),])
  parent <- root
  for (i in (nrow(stacktrace) - 1):1) {
    child <- createMethodNode(stacktrace[i,])
    parent$AddChildNode(child)
    parent <- child
  }
  root
}

createMethodNode <- function(stacktraceRow) {
  className <- stacktraceRow$className
  methodName <- stacktraceRow$methodName
  lineNumber <- stacktraceRow$lineNumber
  fileName <- stacktraceRow$fileName
  node <- Node$new(paste(className, methodName, sep = "."))
  node$className <- className
  node$methodName <- methodName
  node$fileName <- fileName
  node$lineNumber <- lineNumber
  node
}

readFlowMetadata <- function(file) {
  lines <- readLines(file, warn = FALSE)
  frame <- fromJSON(lines)
  res <- list()
  for (i in 1:nrow(frame)) {
    root <- flowAsTree(frame[i, "rootFlow"])
    res[[root$flowId]] <- root
  }
  res
}

flowAsTree <- function(flow) {
  className <- gsub("/", ".", flow$className)
  node <- Node$new(paste(className, flow$methodName, sep = "."))
  node$flowId <- flow$flowId
  node$className <- className
  node$methodName <- flow$methodName
  node$methodDescriptor <- flow$methodDescriptor
  node$fileName <- flow$fileName
  node$firstLine <- flow$firstLine
  node$returnLine <- flow$returnLine
  
  if (!is.na(flow$subflows) && !is.null(flow$subflows)) {
    nested <- flow$subflows[[1]]
    if (!is.null(nested)) {
      for (i in 1:nrow(nested)) {
        node$AddChildNode(flowAsTree(nested[i,]))
      }
    }
  }
  
  node
}