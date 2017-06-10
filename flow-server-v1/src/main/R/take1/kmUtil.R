km <- function(data, maxClusters, plotSteps = FALSE) {
  data.sc <- scale(data[complete.cases(data),])
  
  km <- list(kmeans(data.sc, 1))
  values <- km[[1]]$tot.withinss
  
  for (i in 2:maxClusters) {
    # add a new centroid
    prev <- km[[i - 1]]
    centers <- rbind(prev$centers, newCentroid(data.sc, prev))
    
    km[[i]] <- kmeans(data.sc, centers, iter.max = 20)
    if (plotSteps) {
      plotKm(data.sc, km[[i]]$centers, paste("step", i))
    }
    values[i] <- km[[i]]$tot.withinss
  }
  
  diff2 <- diff(diff(values))
  K <- which.max(diff2) + 1
  if (plotSteps) {
    plot(values, type = "b", main = paste("scaled data"), xlab = "num clusters", ylab = "tot.withinss")
    lines(diff2, col = "blue")
    print(paste("Number of clusters", K))
  }
  
  resKM <- km[[K]]
  scaleAttr <- attributes(data.sc)
  centers <- apply(resKM$centers, MARGIN = 1, FUN = function(c)
    {
      c * scaleAttr$`scaled:scale` + scaleAttr$`scaled:center`
    })
  resKM$centers <- t(centers)
  resKM
}

newCentroid <- function(data, prev) {
  k <- which.max(prev$size)
  cc <- prev$centers[k, ]
  cmembers <- data[names(subset(prev$cluster, prev$cluster == k)), ]
  distances <- apply(cmembers, MARGIN = 1, FUN = function(v) {
    sum((v - cc) ^ 2)
  })
  remote <- names(which.max(distances))
  data[remote, ]
}

plotKm <- function(data, centers, title) {
  plot(data, pch = 16, cex = .5, col="gray", main = title, xlab = "throughput sec", ylab = "duration ms")
  points(centers, pch = 16, cex = .8, col="blue")
}