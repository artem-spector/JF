read.metrics <- function (fileName, folder = "../../../../target/") {
  read.table(file=paste(folder, fileName, sep = ""), sep = " ", na.strings = "null", header = TRUE, row.names = "time")
}

plotFlows <- function(data, flowNum, col = "blue", newPlot = FALSE) {
  thruCols <- paste("throughput_", flowNum, sep = "");
  durCols <- paste("duration_", flowNum, sep = "");

  if (newPlot) {
    minThru <- min(data[,thruCols])
    maxThru <- max(data[,thruCols])
    minDur <- min(data[,durCols])
    maxDur <- max(data[,durCols])
    title <- paste("Flows", min(flowNum), "to",max(flowNum))
    plot(0, 0, type = "n", xlim = c(minThru, maxThru), ylim = c(minDur, maxDur), main = title, xlab = "throughput per sec", ylab = "duration ms")
  }
  
  for (i in 1:length(thruCols)) {
    points(data[,thruCols[i]], data[,durCols[i]], col = col, pch = 16, cex = .5)
  }
}

plotLoad <- function(data) {
  cpuCol <- grep("^cpu", colnames(data))
  memCol <- grep("^mem", colnames(data))
  plot(data[, cpuCol], data[, memCol], xlab = "% cpu", ylab = "used memory MB", pch = 16, cex = .9)
}

clusterFlow <- function(data, flowNum) {
  thruCols <- paste("throughput_", flowNum, sep = "");
  durCols <- paste("duration_", flowNum, sep = "");
  flowData<- subset(data, select = c(thruCols, durCols));
  flowData.sc <- scale(flowData)
  
  km <- list(kmeans(flowData.sc, 1))
  values <- km[[1]]$tot.withinss;
  
  for (i in 2:10) {
    # add a new centroid
    prev <- km[[i-1]]
    centers <- rbind(prev$centers, newCentroid(flowData.sc, prev))

    km[[i]] <- kmeans(flowData.sc, centers, iter.max= 20)
    values[i] <- km[[i]]$tot.withinss
  }
  
  plot(2:10, values[2:10], type = "b", main = paste("scaled flow", flowNum), xlab = "num clusters", ylab = "tot.withinss")
}

sqDist <- function(p1, p2) {
  sum((p1 - p2) ^ 2)
}

newCentroid <- function(flowData, prev) {
  k <- which.max(prev$size)
  cc <- prev$centers[k,]
  cmembers <- flowData[names(subset(prev$cluster, prev$cluster == k)),]
  distances <- apply(cmembers, MARGIN = 1, FUN = function(v) {sum((v-cc)^2)})
  remote <- names(which.max(distances))
  flowData[remote,]
}