rootFlowNumbers <- function(data) {
  nested <- unlist(flowMetadata[, grep("nested", colnames(flowMetadata))])
  rootIds <- flowMetadata[!(flowMetadata[,1] %in% nested), 1]
  flowNum[flowNum$flowId %in% rootIds, "flowNum"]
}

flowSubset <- function(data, flowNum, addTimeColumn = FALSE) {
  tCol <-   paste("throughput_", flowNum, sep = "");
  dCol <- paste("duration_", flowNum, sep = "");
  res <- subset(data, select = c(tCol, dCol));
  colnames(res) <- c("throughput", "duration")
  if (addTimeColumn) {
    res <- cbind(res, time = strptime(rownames(res), format = "%Y.%m.%d_%H:%M:%S"))
  }
  res
}

multiFlow <- function(data, flows) {
  res <- NULL
  for (flowNum in flows) {
    singleFlow <- flowSubset(data, flowNum)
    rownames(singleFlow) <- paste("f", flowNum, rownames(singleFlow), sep = "_")
    res <- rbind(res, singleFlow)
  }
  res
}

# positive and zero value means scalable flow (duration and throughpout increase/decrease together or independently)
# negative value means above -0.5 means limited scalabilty
# negative value below -0.5 means no scalability
flowScalability <- function(data, flowNum) {
  flow <- flowTs(data, flowNum)
  cor(flow$duration, flow$throughput)
}

