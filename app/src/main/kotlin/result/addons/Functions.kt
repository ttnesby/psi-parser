package result.addons

/**
 * Transforms the success value of a Result<T> using the provided transformation function, or propagates the failure.
 *
 * @param transform A function that takes the success value of the current Result<T> and returns a new Result<R>.
 * @return A Result<R> that is the result of applying the transform function if the current Result is successful,
 *         or propagates the failure if the current Result is a failure.
 */

fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
    return fold(
        onSuccess = { value -> transform(value) },
        onFailure = { exception -> Result.failure(exception) }
    )
}

/**
 * Combines a list of `Result` objects into a single `Result` containing a list of all successful values
 * or a failure if any of the results is a failure.
 *
 * @return A `Result` wrapping a list of successful values if all results are successful,
 * or a failure wrapping the first encountered exception if any of the results fail.
 */
fun <T> List<Result<T>>.toResult(): Result<List<T>> {
    return fold(Result.success(emptyList())) { acc, element ->
        acc.fold(
            onSuccess = { list -> element.map { value -> list + value } },
            onFailure = { Result.failure(it) }
        )
    }
}