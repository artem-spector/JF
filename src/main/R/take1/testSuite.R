clearAll <- function() {
  # clear environment
  rm(list = ls(.GlobalEnv), envir = .GlobalEnv)
  
  # source R scripts
  for(file in list.files(pattern = ".R$")) {
    source(file)
  }
}

testKMFlows <- function(flows) {
  clearAll()
  data <- read.metrics("metrics1.dat")
  for (flowNum in flows) 
    kmSingleFlow(data, flowNum)
}

kmSingleFlow <- function(data, flowNum) {
  flowData <- flowSubset(data, flowNum)
  k <- km(flowData, 10)
  plotKm(flowData, k$centers, paste("Flow", flowNum))
}
