read.metrics <- function (fileName, folder = "") {
  read.table(file=paste(folder, fileName, sep = ""), sep = " ", na.strings = "null", header = TRUE, row.names = "time")
}

flowSubset <- function(data, flowNum) {
  tCol <-   paste("throughput_", flowNum, sep = "");
  dCol <- paste("duration_", flowNum, sep = "");
  subset(data, select = c(tCol, dCol));
}


