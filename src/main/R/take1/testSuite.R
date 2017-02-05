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
  nested <- unlist(flowMetadata[, grep("nested", colnames(flowMetadata))])
  rootIds <<- flowMetadata[!(flowMetadata[,1] %in% nested), 1]
  rootFlows <<- flowNum[flowNum$flowId %in% rootIds, "flowNum"]
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
}

sample1 <- function() {
  analyzeSample("../../../test/resources/samples/metrics/1/")
}