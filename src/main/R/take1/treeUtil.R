library(jsonlite)
library(data.tree)
library(digest)

readThreadDumps <- function(file) {
  lines <- readLines(file, warn = FALSE)
  frame <- fromJSON(lines)
  res <- data.frame()

  for (i in 1: nrow(frame)) {
    res <- rbind(res, parseThreadDump(frame[i,]))
  }
  
  res
}

parseThreadDump <- function(data) {
  res <- data.frame()
  for (i in 1:nrow(data$liveThreads[[1]])) {
    threadData <- data$liveThreads[[1]][i,]
    pathStr <- getPathString(threadData$stackTrace[[1]])
    threadId <- sha1(pathStr)
    found <- which(res$threadId == threadId)
    if (length(found) == 0) {
      idx <- nrow(res) + 1
      res[idx, "time"] <- data$time
      res[idx, "threadId"] <- threadId
      res[idx, "status"] <- threadData$threadState
      res[idx, "count"] <- 1
      res[idx, "pathStr"] <- pathStr
    } else {
      idx <- found[1]
      res[idx, "count"] <- res[idx, "count"] + 1
    }
  }
  res
}

getPathString <- function(trace) {
  pathStr <- ""
  if (nrow(trace) > 0) {
    for (i in 1:nrow(trace)) {
      pathStr <- paste(paste(trace[i, "className"], trace[i, "methodName"], sep = "."), pathStr, sep = "/")
    }
  }
  pathStr
}

readSnapshots <- function(file) {
  lines <- readLines(file, warn = FALSE)
  frame <- fromJSON(lines)
  res <- list()
  for (i in 1:nrow(frame)) {
    snapshot <- frame[i,]$snapshotJson
    duration <- (snapshot$endTime - snapshot$startTime) / 1000 # timestamps are in millis
    for (i in 1:nrow(snapshot$flows[[1]])) {
      root <- parseFlow(snapshot$flows[[1]][i,], NULL, duration)
      root$snapshotStart <- snapshot$startTime
      root$snapshotDuration <- duration
      root$time <- frame[i, "time"]
      res <- append(res, root)
    }
  }
  res
}

parseFlow <- function(data, rootCumulativeTime, snapshotDuration) {
  flow <- Node$new(paste(gsub("/", ".", data$className), data$methodName, sep = "."), flowId = data$key,
                    className = data$class, methodName = data$methodName, methodDescriptor = data$methodDescriptor,
                    file = data$file, firstLine = data$firstLine, returnLine = data$returnLine)

  flow$cumulativeDurationSec <- nanoToSec(data$statistics$cumulative)
  flow$minDurationSec <- nanoToSec(data$statistics$min)
  flow$maxDurationSec <- nanoToSec(data$statistics$max)
  flow$invocationCount = data$statistics$count
  if (is.null(rootCumulativeTime)) rootCumulativeTime <- flow$cumulativeDurationSec
  
  flow$avgDurationSec <- flow$cumulativeDurationSec / flow$invocationCount
  flow$throughput <- flow$invocationCount / snapshotDuration
  flow$weight <- flow$cumulativeDurationSec / rootCumulativeTime
  flow$ownWeight <- flow$weight
  
  if (!is.null(data$subflows) && length(data$subflows) == 1 && !is.null(data$subflows[[1]])) {
    for (i in 1:nrow(data$subflows[[1]])) {
      child <- parseFlow(data$subflows[[1]][i,], rootCumulativeTime, snapshotDuration)
      flow$AddChildNode(child)
      flow$ownWeight <- flow$ownWeight - child$weight
    }
  }
  flow
}

nanoToSec <- function(nano) {
  nano / 1000000000
}

extractFeaturesFromRootFlows <- function(rootFlows, recursive = FALSE) {
  res <- data.frame()
  for(root in rootFlows) {
    res <- flowFeatures(root, res, root, recursive)
  }
  res
}

flowFeatures <- function(flow, frame, root, recursive) {
  row <- nrow(frame) + 1
  frame[row, "flowId"] <- flow$flowId
  frame[row, "mtd"] <- flow$name
  frame[row, "minTime"] <- flow$minDurationSec
  frame[row, "maxTime"] <- flow$maxDurationSec
  frame[row, "avgTime"] <- flow$avgDurationSec
  frame[row, "throughput"] <- flow$throughput
  frame[row, "weight"] <- flow$weight
  frame[row, "ownWeight"] <- flow$ownWeight
  frame[row, "time"] <- root$time

  if (recursive) {
    if (!is.null(flow$children)) {
      for(nested in flow$children) {
        frame <- flowFeatures(nested, frame, root, recursive)
      }
    }
  }
  
  frame
}

enrichFlowsWithThreadDumps <- function(rootFlows, allDumps) {
  # find the snapshot times 
  snapshotTimes <- c()
  for (r in rootFlows)
    snapshotTimes <- append(snapshotTimes, r$time)
  snapshotTimes <- unique(snapshotTimes)
  
  # loop by snapshots 
  for (s in snapshotTimes) {
    # extract root flows, their instrumented methods, and thread dumps to be merged
    flows <- list()
    dumps <- list()
    instr <- c()
    if (!is.na(s)) {
      from <- s - 5000
      to <- s + 1000
      for (r in rootFlows) {
        if (!is.na(r$time) && r$time == s) {
          flows[[length(flows) + 1]] <- r
          instr <- unique(append(instr, r$Get("name")))
        }
      }
      threads <- allDumps[allDumps$time >= from & allDumps$time <= to,]
    }
    
    # add dump data to flows
    if (length(flows) > 0 && nrow(threads) > 0) {
      for (f in flows) {
        addThreadsToFlow(f, threads, instr)
      }
    }
  }
  
  TRUE
}

addThreadsToFlow <- function(flow, threads, instr) {
  threadIds <- unique(threads$threadId)
  for (id in threadIds) {
    # in the stacktrace path keep only instrumented methods of the flow 
    t <- threads[threads$threadId == id,][1,]
    trace <- unlist(strsplit(t$pathStr, split = "/"))
    path <- trace[trace %in% instr]
    
    # climb the stacktrace path on the flow tree
    len <- length(path)
    if (len > 0 && flow$name == path[1]) {
      found <- flow
      if (len > 1)
        found <- flow$Climb(path[-1])
      
      if (!is.null(found)) {
        # add hotspot as a child node
        hotspotList <- subset(found$children, found$children$name == id)
        hotspotNode <- NULL
        if (length(hotspotList) == 0) {
          hotspotNode = Node$new(id, status = t[1,"status"], trace = trace, numThreads = 0, numDumps = 0)
          found$AddChildNode(hotspotNode)
        } else {
          hotspotNode <- hotspotList[[1]]
        }
        hotspotNode$numThreads <- hotspotNode$numThreads + t$count
        hotspotNode$numDumps <- hotspotNode$numDumps + 1
      }
    }
  }
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
  node$maxTime <- data$maxTime
  node$minTime <- data$minTime
  node$cumulativeTime <- data$cumulativeTime
  node$count <- data$count
  
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

isHotspotNode <- function(node) {
  is.null(node$ownWeight)
}

nodeShape <- function(node) {
  if (isHotspotNode(node))
    return("ellipse")
  else 
    return("box")
}

nodeLabel <- function(node) {
  if (isHotspotNode(node)) {
    return(node$status)
  } else {
    weightStr <- paste0("own time: ", format(node$ownWeight * 100, scientific = FALSE, digits = 1, nsmall = 1), "%")
    if (node$isRoot)
      return (
        paste0(node$name, 
          "\navg duration: ", format(node$avgDurationSec, digits = 3), "sec",
          "\nthroughput: ", format(node$throughput, digits = 2, nsmall = 1), "/sec",
          '\n', weightStr
        ))
    else 
      return (paste0(node$name, '\n', weightStr))
  }
}

nodeTooltip <- function(node) {
  if (isHotspotNode(node))
    return (paste0(node$trace, collapse = "\n"))
  else 
    return (paste0(node$file, " ", node$firstLine, "..", node$returnLine))
}

nodeFillColor <- function(node) {
  if (isHotspotNode(node)) {
    return(switch(node$status, "RUNNABLE" = "green", "WAITING" = "yellow", "TIMED_WAITING" = "yellow", "BLOCKED" = "red"))
  } else {
    rank <- as.integer(node$ownWeight * 8) + 1 
    return(paste0("/blues9/", rank))
  }
}

nodeFontColor <- function(node) {
  if (isHotspotNode(node)) {
    return("black")
  } else if (node$ownWeight >= 0.65) {
    return("white")
  } else {
    return("black")
  }
}

edgeLabel <- function(node) {
  if (isHotspotNode(node))
    return (format(round(node$numThreads / node$numDumps)))
  else if (!node$isRoot) 
    return (format(node$invocationCount / node$parent$invocationCount, scientific = FALSE, digits = 1))
  else
    return ("")
}

edgeArrowhead <- function(node) {
  if (isHotspotNode(node))
    return ("none")
  else
    return ("normal")
}

edgeLine <- function(node) {
  if (isHotspotNode(node))
    return ("dashed")
  else
    return ("solid")
}

plotFlow <- function(flow) {
  SetEdgeStyle(flow, fontname = 'helvetica', label = edgeLabel, arrowhead = edgeArrowhead, style = edgeLine)
  SetNodeStyle(flow, fontname = 'helvetica', penwidth = "1px", shape = nodeShape, style = "filled,rounded", 
               label = nodeLabel, tooltip = nodeTooltip, fillcolor = nodeFillColor, fontcolor = nodeFontColor)
  plot(flow)
}
