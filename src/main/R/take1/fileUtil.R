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

clusterFlow <- function(data, flowNum, plotSteps = FALSE) {
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
    if (plotSteps) {
      plotKm(flowData.sc, km[[i]]$centers, paste("flow", flowNum, "step", i))
    }
    values[i] <- km[[i]]$tot.withinss
  }
  
  diff2 <- diff(diff(values))
  K <- which.max(diff2) + 1 
  if (plotSteps) {
    plot(values, type = "b", main = paste("scaled flow", flowNum), xlab = "num clusters", ylab = "tot.withinss")
    lines(diff2, col="blue")
    print(paste("Number of clusters", K))
  }
  
  scaleAttr <- attributes(flowData.sc)
  centers <- apply(km[[K]]$centers, MARGIN = 1, FUN = function(c) 
    {c * scaleAttr$`scaled:scale` + scaleAttr$`scaled:center`})
  t(centers)
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

plotKm <- function(data, centers, title) {
  plot(data, pch = 16, cex = .5, col="gray", main = title)
  points(centers, pch = 16, cex = .8, col="blue")
}