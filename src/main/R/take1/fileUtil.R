read.metrics <- function (fileName, folder = "../../../../target/") {
  read.table(file=paste(folder, fileName, sep = ""), header = TRUE)
}

plotFlow <- function(data, flowNum, col = "blue", newPlot = FALSE) {
  if (newPlot) {
    plot(c(0,200), c(0,400), type = "n", xlab = "throughput per sec", ylab = "duration ms")
  }
  
  for (i in flowNum) {
    thruCol <- paste("X", i, "_freq", sep = "");
    durCol <- paste("X", i, "_flowDurationAvg", sep = "");
    points(data[,thruCol], data[,durCol], col = col, pch = 16, cex = .5)
  }
}