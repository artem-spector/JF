read.metrics <- function (fileName, folder = "../../../../target/") {
  read.table(file=paste(folder, fileName, sep = ""), header = TRUE)
}