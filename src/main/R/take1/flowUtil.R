read.metrics <- function (fileName, folder = "") {
  read.table(file=paste(folder, fileName, sep = ""), sep = " ", na.strings = "null", header = TRUE, row.names = "time")
}

allFlows <- function(data) {
  durationCols <- grep("^duration_", colnames(data), value = TRUE)
  as.integer(substr(durationCols, 10, 1000))
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
flowScalability <- function(data, flowNum, plot = FALSE) {
  flow <- flowTs(data, flowNum)
  correlation <- ccf(flow$duration, flow$throughput, type = "correlation", lag.max = 0, plot = plot)
  correlation[0]$acf[1,1,1]
}

