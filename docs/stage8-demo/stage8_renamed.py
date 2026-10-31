# Line inserted after a breakpoint was saved in stage8_main.py.
from stage8_helper import twice

value = twice(21)
print("stage8-result:", value)
raise ValueError("stage8-exception")
