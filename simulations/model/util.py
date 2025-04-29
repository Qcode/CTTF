def split_by_percent(percent, total):
    first = round(percent * total)
    second = total - first
    return first, second
