class Ledger
  def initialize(entries)
    @entries = entries
  end

  def balance(kind)
    total = 0
    @entries.each do |entry|
      if entry[:kind] == kind && entry[:amount].positive?
        total += entry[:amount]
      elsif entry[:kind] == :refund
        total -= entry[:amount]
      end
    end
    total
  end

  def label(code)
    case code
    when :a then "alpha"
    when :b then "beta"
    when :c then "gamma"
    else "other"
    end
  end

  def outer
    def inner; 1; end
    inner + 1
  end

  def size = @entries.size

  define_method(:empty?) { @entries.empty? }
end
