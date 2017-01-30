read.metrics <- function (fileName, folder = "../../../../target/") {
  read.table(file=paste(folder, fileName, sep = ""), sep = " ", na.strings = "null", header = TRUE, row.names = "time")
}

plotFlow <- function(data, flowNum, col = "blue", newPlot = FALSE) {
  if (newPlot) {
    plot(c(0,200), c(0,400), type = "n", xlab = "throughput per sec", ylab = "duration ms")
  }
  
  for (i in flowNum) {
    thruCol <- paste("throughput_", i, sep = "");
    durCol <- paste("duration_", i, sep = "");
    points(data[,thruCol], data[,durCol], col = col, pch = 16, cex = .5)
  }
}