local smartcard = {}

local function resolveReader(reader)
    if type(reader) == "string" then
        local wrapped = peripheral.wrap(reader)
        if not wrapped then error("No peripheral named " .. reader, 3) end
        return wrapped
    end
    return reader
end

local function readFile(path)
    local handle = fs.open(path, "r")
    if not handle then error("Cannot open " .. path, 3) end
    local contents = handle.readAll()
    handle.close()
    return contents
end

function smartcard.issueFromFile(reader, path)
    reader = resolveReader(reader)
    return reader.issueSource(readFile(path))
end

local function collect(base, rel, out)
    local here = rel == "" and base or fs.combine(base, rel)
    for _, name in ipairs(fs.list(here)) do
        local childRel = rel == "" and name or fs.combine(rel, name)
        local child = fs.combine(base, childRel)
        if fs.isDir(child) then
            collect(base, childRel, out)
        else
            out["/" .. childRel] = readFile(child)
        end
    end
end

function smartcard.issueFromDir(reader, path)
    reader = resolveReader(reader)
    local files = {}
    collect(path, "", files)
    if not files["/main.lua"] then error("Directory must contain main.lua", 2) end
    return reader.issueFiles(files)
end

return smartcard
