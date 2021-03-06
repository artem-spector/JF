kmSingleFlow <- function(flows) {
  for (flowNum in flows) {
    flowData <- flowSubset(metrics, flowNum)
    k <- km(flowData, 10)
    plotKm(flowData, k$centers, paste("Flow", flowNum))
  }
}

kmMultiFlow <- function(flows, plotSteps = FALSE) {
  flowData <- multiFlow(metrics, flows)
  k <- km(flowData, 10, plotSteps)
  plotKm(flowData, k$centers, paste("Flow", min(flows), ":", max(flows)))
}

correlationRootFlows <- function(plotFlows = TRUE) {
  if (plotFlows) {
    plotFlowTs(metrics, rootFlows)
  }
  correlationMatrix(metrics, rootFlows, metric = "both")
}

loadSample <- function(folder) {
  # clear environment
  rm(list = ls(.GlobalEnv), envir = .GlobalEnv)
  
  # source R scripts
  for(file in list.files(pattern = ".R$")) {
    source(file)
  }

  # load metrics  
  print(paste("Loading data from folder", folder))
  file <- paste(folder, "metrics.dat", sep = "")
  metrics <<- read.table(file, sep = " ", na.strings = "null", header = TRUE, row.names = "time")

  # load flow metadata
  file <- paste(folder, "flowMetadata.dat", sep = "")
  flowMetadata <<- read.table(file, sep = " ", na.strings = "null", header = TRUE, stringsAsFactors = FALSE)
  
  # load flow numbers to ID translation
  file <- paste(folder, "flowNum.dat", sep = "")
  flowNum <<- read.table(file, sep = " ", na.strings = "null", header = TRUE, stringsAsFactors = FALSE)
  
  # extract root flows
  rootFlows <<- rootFlowNumbers(flowMetadata, flowNum)
}

analyzeSample <- function(folder = "../../../../target/testContinuous-temp/") {
  loadSample(folder)
  kmSingleFlow(rootFlows)
  plotFlowTs(metrics, rootFlows)
  cm <- correlationMatrix(metrics, rootFlows, metric = "both")
  cK <- km(cm, nrow(cm) - 1)
  numClusters <- nrow(cK$centers)
  print(paste("Detected", numClusters, "groups of root flows:"))
  for (i in 1:numClusters) {
    print(paste("group", i, ":")) 
    print(row.names(cm)[cK$cluster == i])
  }
  for (f in rootFlows) {
    print(paste("Flow", f, "scalability =", flowScalability(metrics, f)))
  }
}

# main method, the number is the sample folder
sample <- function(num) {
  analyzeSample(paste("../../../test/resources/samples/metrics/", num, "/", sep = ""))
}

# tree analysis
analyzeTrees <- function(folder = "../../../../target/testContinuous-temp/", doDebug = TRUE) {
  rootFlows <- readSnapshots(paste(folder, "snapshots.json", sep = ""))

  if (doDebug) {
    print(paste("loaded", length(rootFlows), "root flows"))
    rootFlows <<- rootFlows
  }

  threadDumps <- readThreadDumps(paste(folder, "liveThreads.json", sep = ""))
  if (doDebug) {
    print(paste("loaded", length(threadDumps), "threadDumps"))
    threadDumps <<- threadDumps
  }

  enrichFlowsWithThreadDumps(rootFlows, threadDumps)
  if (doDebug) {
    print("flows eriched with thread dumps data")
  }
      
  df <- extractFeaturesFromRootFlows(rootFlows)
  rootIds <- unique(df[, "flowId"])
  
  if (doDebug) {
    print(paste(length(rootIds), "distinct root flows detected"))
  }
  
  for (id in rootIds)
    calculateFlowScalability(subset(df, df[, "flowId"] == id), doDebug)
}

calculateFlowScalability <- function(data, doDebug) {
  flowId <- data[1,"flowId"]
  durationThroughputTs <- ts(subset(data, select = c("avgTime", "throughput")))
  if (doDebug) {
    plot.ts(durationThroughputTs, main = paste(nrow(data), "observations of", flowId))
  }

  # positive and zero value means scalable flow (duration and throughpout increase/decrease together or independently)
  # negative value means above -0.5 means limited scalabilty
  # negative value below -0.5 means no scalability
  scalability <- cor(data[, "avgTime"], data[, "throughput"])
  if (doDebug) {
    print(paste("flow", flowId, "duration to throughput correlation is", scalability))
  }
  scalability
}
