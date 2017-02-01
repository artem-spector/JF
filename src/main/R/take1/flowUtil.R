read.metrics <- function (fileName, folder = "") {
  read.table(file=paste(folder, fileName, sep = ""), sep = " ", na.strings = "null", header = TRUE, row.names = "time")
}

allFlows <- function(data) {
  durationCols <- grep("^duration_", colnames(data), value = TRUE)
  as.integer(substr(durationCols, 10, 1000))
}

flowSubset <- function(data, flowNum) {
  tCol <-   paste("throughput_", flowNum, sep = "");
  dCol <- paste("duration_", flowNum, sep = "");
  subset(data, select = c(tCol, dCol));
}

multiFlow <- function(data, flows) {
  res <- NULL
  for (flowNum in flows) {
    singleFlow <- flowSubset(data, flowNum)
    rownames(singleFlow) <- paste("f", flowNum, rownames(singleFlow), sep = "_")
    colnames(singleFlow) <- c("throughput", "duration")
    res <- rbind(res, singleFlow)
  }
  res
}