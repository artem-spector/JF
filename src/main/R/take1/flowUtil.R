flowId2Num <- function(flowNumTable, ids) {
  flowNumTable[flowNumTable$flowId %in% ids, "flowNum"]
}

flowNum2Id <- function(flowNumTable, nums) {
  flowNumTable[flowNumTable$flowNum %in% nums, "flowId"]
}

rootFlowNumbers <- function(flowMetadata, flowNum) {
  nested <- unlist(flowMetadata[, grep("nested", colnames(flowMetadata))])
  rootIds <- flowMetadata[!(flowMetadata[,1] %in% nested), 1]
  flowId2Num(flowNum, rootIds)
}

nestedFlows <- function(flowNum, flowMetadata, flowNumTable, recursive = FALSE) {
  flowId <- flowNum2Id(flowNumTable, flowNum)
  nested <- flowMetadata[flowMetadata$flowId == flowId, grep("nested", colnames(flowMetadata))]
  nested <- nested[!is.na(nested)]
  nested <- flowId2Num(flowNumTable, nested)
  
  res <- nested
  if (recursive) {
    for (f in nested) {
      res <- append(res, nestedFlows(f, flowMetadata, flowNumTable, recursive = TRUE))
    }
  }
  unique(res)
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
flowScalability <- function(data, flowNum) {
  flow <- flowTs(data, flowNum)
  cor(flow$duration, flow$throughput)
}

#------------------------------------------------
flowVariance <- function(flows, data, flowMetadata, flowNumTable) {
  res <- list()
  for (f in flows) {
    row <- c()
    row[as.character(f)] <- var(flowTs(data, f)$duration)
    nested <- nestedFlows(f, flowMetadata, flowNum, TRUE)
    for (n in nested)
      row[as.character(n)] <- var(flowTs(data, n)$duration)
    res[[as.character(f)]] <- row
  }
  res
}

flowDurationNestedCorrelation <- function(flow, data, flowMetadata, flowNumTable) {
  nested <- nestedFlows(flow, flowMetadata, flowNumTable, TRUE)
  fts <- flowTs(data, flow)
  res <- c()
  for (n in nested)
    res[as.character(n)] <- cor(fts$duration, flowTs(data, n)$duration)
  res
}

