flowTs <- function(data, flowNum) {
  f <- flowSubset(data, flowNum)
  list(duration = ts(f$duration), throughput = ts(f$throughput), both = ts(f))
}

flowCCF <- function(data, f1, f2, type = c("correlation", "covariance")) {
  fts1 <- flowTs(data, f1)
  fts2 <- flowTs(data, f2)
  list(
    duration = ccf(fts1$duration, fts2$duration, type = type, na.action = na.pass, plot = FALSE),
    throughput = ccf(fts1$throughput, fts2$throughput, type = type, na.action = na.pass, plot = FALSE)
  )
}

correlationMatrix <- function(data, flows, metric = c("throughput", "duration", "both")) {
  names <- paste("F", flows, sep = "")
  n <- length(flows)
  m <- matrix(0, n, n, dimnames = list(names, names))
  for(i in 1:n) {
    for(j in 1:n) {
      c <- flowCCF(data, flows[i], flows[j])
      max.dc <- max(abs(c$duration$acf))
      max.tc <- max(abs(c$throughput$acf))
      m[i, j] <- switch(metric, throughput = max.tc, duration = max.dc, both = (max.tc + max.dc) / 2)
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
