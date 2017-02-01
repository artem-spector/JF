clearAll <- function() {
  # clear environment
  rm(list = ls(.GlobalEnv), envir = .GlobalEnv)
  
  # source R scripts
  for(file in list.files(pattern = ".R$")) {
    source(file)
  }
}

kmSingleFlow <- function(flows) {
  clearAll()
  data <- read.metrics("metrics1.dat")
  for (flowNum in flows) {
    flowData <- flowSubset(data, flowNum)
    k <- km(flowData, 10)
    plotKm(flowData, k$centers, paste("Flow", flowNum))
  }
}

kmMultiFlow <- function(flows, plotSteps = FALSE) {
  clearAll()
  data <- read.metrics("metrics1.dat")
  flowData <- multiFlow(data, flows)
  k <- km(flowData, 10, plotSteps)
  plotKm(flowData, k$centers, paste("Flow", min(flows), ":", max(flows)))
}

