flowTs <- function(data, flowNum) {
  f <- flowSubset(data, flowNum)
  f[is.na(f)] <- 0
  list(duration = ts(f$duration), throughput = ts(f$throughput), both = ts(f))
}

flowCorrelation <- function(data, f1, f2) {
  fts1 <- flowTs(data, f1)
  fts2 <- flowTs(data, f2)
  list(
    duration = cor(fts1$duration, fts2$duration),
    throughput = cor(fts1$throughput, fts2$throughput)
  )
}

correlationMatrix <- function(data, flows, metric = c("throughput", "duration", "both")) {
  names <- paste("F", flows, sep = "")
  n <- length(flows)
  m <- matrix(0, n, n, dimnames = list(names, names))
  for(i in 1:n) {
    for(j in 1:n) {
      c <- flowCorrelation(data, flows[i], flows[j])
      dc <- abs(c$duration)
      tc <- abs(c$throughput)
      m[i, j] <- switch(metric, throughput = tc, duration = dc, both = (tc + dc) / 2)
    }
  }
  m
}

plotFlowTs <- function(data, flowNum) {
  for (f in flowNum) {
    plot.ts(ts(flowSubset(data, f)), main = paste("flow", f))
  }
}

plotLoadTs <- function(data) {
  cpuCol <- grep("^cpu", colnames(data))
  memCol <- grep("^mem", colnames(data))
  l <- subset(data, select = c(cpuCol, memCol))
  colnames(l) <- c("cpu", "mem")
  
  plot.ts(ts(l), main = "JVM load")
}
