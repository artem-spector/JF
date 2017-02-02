clearAll <- function() {
  # clear environment
  rm(list = ls(.GlobalEnv), envir = .GlobalEnv)
  
  # source R scripts
  for(file in list.files(pattern = ".R$")) {
    source(file)
  }
  
  metrics <<- read.metrics("metrics1.dat")
  rootFlows <<- c(20, 44, 85, 12, 127, 137)
}

kmSingleFlow <- function(flows) {
  clearAll()
  for (flowNum in flows) {
    flowData <- flowSubset(metrics, flowNum)
    k <- km(flowData, 10)
    plotKm(flowData, k$centers, paste("Flow", flowNum))
  }
}

kmMultiFlow <- function(flows, plotSteps = FALSE) {
  clearAll()
  flowData <- multiFlow(metrics, flows)
  k <- km(flowData, 10, plotSteps)
  plotKm(flowData, k$centers, paste("Flow", min(flows), ":", max(flows)))
}

correlationRootFlows <- function(plotFlows = TRUE) {
  clearAll()
  if (plotFlows) {
    plotFlowTs(metrics, rootFlows)
  }
  correlationMatrix(metrics, rootFlows, metric = "both")
}
