library(jsonlite)
library(data.tree)

readSnapshots <- function(file) {
  lines <- readLines(file, warn = FALSE)
  frame <- fromJSON(lines)
  res <- list()
  for (i in 1:nrow(frame)) {
    snapshot <- frame[i,]$snapshotJson
    duration <- (snapshot$endTime - snapshot$startTime) / 1000 # timestamps are in millis
    for (i in 1:nrow(snapshot$flows[[1]])) {
      root <- parseFlow(snapshot$flows[[1]][i,])
      root$snapshotDuration <- duration
      res <- append(res, root)
    }
  }
  res
}

parseFlow <- function(data) {
  flow <- Node$new(paste(gsub("/", ".", data$className), data$methodName, sep = "."), flowId = data$key,
                    className = data$class, methodName = data$methodName, methodDescriptor = data$methodDescriptor,
                    file = data$file, firstLine = data$firstLine, returnLine = data$returnLine)
  flow$stat <- list(
    min = nanoToSec(data$statistics$min), 
    max = nanoToSec(data$statistics$max), 
    cumulative = nanoToSec(data$statistics$cumulative), 
    count = data$statistics$count
  )
  if (!is.null(data$subflows) && length(data$subflows) == 1 && !is.null(data$subflows[[1]])) {
    for (i in 1:nrow(data$subflows[[1]])) {
      flow$AddChildNode(parseFlow(data$subflows[[1]][i,]))
    }
  }
  flow
}

nanoToSec <- function(nano) {
  nano / 1000000000
}

flowsAsDataframe <- function(flows, recursive = FALSE) {
  res <- data.frame()
  for(flow in flows) {
    res <- addFlowToDataframe(flow, res, recursive)
  }
  res
}

addFlowToDataframe <- function(flow, frame, recursive) {
  row <- nrow(frame) + 1
  frame[row, "flowId"] <- flow$flowId
  frame[row, "minTime"] <- flow$stat$min
  frame[row, "maxTime"] <- flow$stat$max
  frame[row, "cumulativeTime"] <- flow$stat$cumulative
  frame[row, "count"] <- flow$stat$count
  
  if (recursive) {
    if (!is.null(flow$children)) {
      for(nested in flow$children) 
        frame <- addFlowToDataframe(nested, frame, TRUE)
    }
  }
  
  frame
}

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
    root <- createFlowMetadataNode(frame[i, "rootFlow"])
    root$instrumentedMethods <- unique(root$Get("name"))
    res[[root$flowId]] <- root
  }
  res
}

readFlowData <- function(file) {
  lines <- readLines(file, warn = FALSE)
  frame <- fromJSON(lines)
  res <- list()
  for (i in 1:nrow(frame)) {
    print(paste("build tree", i, "of", nrow(frame)))
    
    root <- createFlowDataNode(frame[i, "rootFlow"])
    root$duration <- frame[i, "snapshotDurationSec"]
    res <- append(res, root)
  }
  res
}

createFlowMetadataNode <- function(flow) {
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
        node$AddChildNode(createFlowMetadataNode(nested[i,]))
      }
    }
  }
  
  node
}

createFlowDataNode <- function(data) {
  node <- Node$new(data$flowId)
  node$stat$maxTime <- data$maxTime
  node$stat$minTime <- data$minTime
  node$stat$cumulativeTime <- data$cumulativeTime
  node$stat$count <- data$count
  
  subflows <- data$subflows
  if (!is.na(subflows) && !is.null(subflows)) {
    nested <- subflows[[1]]
    if (!is.null(nested)) {
      for (i in 1:nrow(nested)) {
        node$AddChildNode(createFlowDataNode(nested[i,]))
      }
    }
  }
  
  node
}

stacktracePathInFlow <- function(stacktrace, flow) {
  # in the stacktrace path keep only instrumented methods of the flow 
  path <- stacktrace$leaves[[1]]$path
  path <- path[path %in% flow$instrumentedMethods]

  # climb the stacktrace path on the flow tree
  found <- NULL
  len <- length(path)
  if (len > 0 && flow$name == path[1]) {
    found <- path
    if (len > 1)
      found <- flow$Climb(path[-1])$path
  }

  found
}

mapFlowsToThreads <- function(flows, threads) {
  numFlows <- length(flows)
  numThreads <- length(threads)

  for (i in 1:numFlows) {
    flow <- flows[[i]]
    for (j in 1:numThreads) {
      thread <- threads[[j]]
      path <- stacktracePathInFlow(thread, flow)
      if (!is.null(path)) {
        threadId <- names(threads)[j]
        flow$traces[[threadId]]$path <- path
        flow$traces[[threadId]]$trace <- thread$leaves[[1]]$path
      }
    }
  }
}

plotFlowWithHotspots <- function(flow) {
  if (isLeaf(flow)) {
    print(paste("Flow", flow$flowId, "has a single node", flow$name, ", not plotting it"))
  } else {
    SetNodeStyle(flow, keepExisting = FALSE, inherit = TRUE, penwidth = "2px", shape = "box", style = "rounded")
    for (trace in flow$traces) {
      spot <- NULL
      if (length(trace$path) == 1) {
        spot <- flow
      } else {
        spot <- flow$Climb(trace$path[-1])
      }
      if (!is.null(spot)) {
        text <- paste(trace$trace, collapse = " ")
        SetNodeStyle(spot, inherit = FALSE, keepExisting = FALSE, penwidth = "5px", tooltip = text)
      }
    }
    print(paste("Plotting flow", flow$flowId))
    plot(flow)
  }
}

