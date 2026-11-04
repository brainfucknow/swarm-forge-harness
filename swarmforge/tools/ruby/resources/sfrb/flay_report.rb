# Writes flay's clones as JSON with every side's file, line and line_max;
# flay's own text report has start lines only.
#
#   ruby flay_report.rb MASS OUTPUT FILE...
require "flay"
require "json"

mass, output, *files = ARGV
flay = Flay.new(mass: Integer(mass))
flay.process(*files)
clones = flay.analyze.map do |item|
  nodes = flay.hashes[item.structural_hash].sort_by { |node| [node.file, node.line] }
  {mass: item.mass, sides: nodes.map { |node| {file: node.file, line: node.line, line_max: node.line_max} }}
end
File.write(output, JSON.generate(clones: clones))
